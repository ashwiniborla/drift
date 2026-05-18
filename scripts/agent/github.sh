#!/usr/bin/env bash
# github.sh — GitHub Enterprise Server REST helper for harness pipeline
#
# Targets a single GHES instance — the Flipkart internal GitHub at
# https://github.fkinternal.com — but every URL/host is configurable so the
# script works equally well against any GHES base URL. github.com is also
# accepted (api.github.com is auto-derived).
#
# Usage:
#   github.sh setup                                  — interactive credential setup
#   github.sh test-auth                              — verify credentials work
#   github.sh push-branch [<remote>]                 — push current branch (default remote: origin)
#   github.sh create-pr <title> <body-md-file> [<base-branch>]
#                                                   — create PR for current branch (base default: default-branch)
#   github.sh ensure-pr <title> <body-md-file> [<base-branch>]
#                                                   — push + create-pr if no PR exists, else update existing PR body
#   github.sh get-pr-for-branch [<branch>]           — emit PR number for a branch (current branch if omitted)
#   github.sh get-pr-comments <pr-number>            — emit all PR review + issue comments
#   github.sh check-approved <pr-number>             — exit 0 if PR has APPROVED review; 1 otherwise
#   github.sh comment-pr <pr-number> <text>          — add an issue-level comment to the PR
#   github.sh resolve-pr <url-or-number>             — extract PR number from URL or pass through
#
# Auth resolution order (first match wins):
#   1. GITHUB_TOKEN env var
#   2. ~/.harness/secrets/github.credentials (global, persisted by 'setup' — survives across repos and sessions)
#   3. .github-credentials file in repo root (gitignored, created by 'setup')
#
# Token = a Personal Access Token (classic). Required scopes:
#   - repo               (read/write code, PRs, issue comments)
#   - read:org           (resolve CODEOWNERS / team membership for `check-approved`)
# On GHES, generate at: <base-url>/settings/tokens
#
# Base URL resolution order (must include the scheme + host, no trailing slash):
#   1. GITHUB_BASE_URL env var (e.g., https://github.fkinternal.com)
#   2. github-base-url field in harness-state.md
#   3. base-url field in .github-credentials
#   4. Default: derived from `git remote get-url origin`
#
# API URL is derived: github.com → api.github.com; <host> for GHES → <host>/api/v3
#
# SECURITY:
#   - .github-credentials is gitignored (setup adds it)
#   - Tokens are NEVER stored in harness-state.md (which is committed)
#   - Credential file has 600 permissions
#   - All HTTPS, certificate validation enforced (no -k / --insecure)

set -euo pipefail

# --- Paths -------------------------------------------------------------------
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
HARNESS_STATE="${REPO_ROOT}/harness-state.md"
CRED_FILE="${REPO_ROOT}/.github-credentials"
GLOBAL_CRED_FILE="${HOME}/.harness/secrets/github.credentials"

# --- Helpers: portable key-value extraction ---------------------------------
# `grep -oP` with \K is GNU-only and breaks on macOS BSD grep, so we use awk.
# We split on the FIRST `:` so URL values (https://...) survive intact.
read_kv() {
  local file="$1"
  local key="$2"
  [ -f "$file" ] || { echo ""; return; }
  awk -v k="$key" '
    BEGIN { pat = "^[ \t]*" k ":" }
    $0 ~ pat {
      sub(pat "[ \t]*", "", $0)
      sub(/[ \t\r]+$/, "", $0)
      print
      exit
    }
  ' "$file" 2>/dev/null || echo ""
}

# --- Resolve base URL --------------------------------------------------------
DEFAULT_GITHUB_URL="https://github.fkinternal.com"

GITHUB_BASE_URL="${GITHUB_BASE_URL:-}"

if [ -z "$GITHUB_BASE_URL" ] && [ -f "$HARNESS_STATE" ]; then
  GITHUB_BASE_URL=$(read_kv "$HARNESS_STATE" "github-base-url")
fi

if [ -z "$GITHUB_BASE_URL" ] && [ -f "$GLOBAL_CRED_FILE" ]; then
  GITHUB_BASE_URL=$(read_kv "$GLOBAL_CRED_FILE" "base-url")
fi

if [ -z "$GITHUB_BASE_URL" ] && [ -f "$CRED_FILE" ]; then
  GITHUB_BASE_URL=$(read_kv "$CRED_FILE" "base-url")
fi

# Derive base URL from origin remote when nothing else is configured. This
# avoids hard-coding `github.fkinternal.com` for users on github.com.
if [ -z "$GITHUB_BASE_URL" ]; then
  origin_url=$(git -C "$REPO_ROOT" config --get remote.origin.url 2>/dev/null || echo "")
  if [ -n "$origin_url" ]; then
    # Strip git@host: → https://host  and  https://host/owner/repo(.git) → https://host
    case "$origin_url" in
      git@*) host=${origin_url#git@}; host=${host%%:*}; GITHUB_BASE_URL="https://${host}" ;;
      https://*|http://*) GITHUB_BASE_URL=$(echo "$origin_url" | awk -F/ '{print $1"//"$3}') ;;
    esac
  fi
fi

GITHUB_BASE_URL="${GITHUB_BASE_URL:-$DEFAULT_GITHUB_URL}"

# Strip any trailing slash so we can concatenate paths cleanly.
GITHUB_BASE_URL="${GITHUB_BASE_URL%/}"

# --- Derive API URL ----------------------------------------------------------
# github.com → https://api.github.com (no /api/v3 prefix)
# <ghes>     → <ghes>/api/v3
github_api_url() {
  case "$GITHUB_BASE_URL" in
    https://github.com|http://github.com)
      echo "https://api.github.com"
      ;;
    *)
      echo "${GITHUB_BASE_URL}/api/v3"
      ;;
  esac
}

GITHUB_API_URL="$(github_api_url)"

# --- Load auth token ---------------------------------------------------------
resolve_token() {
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    echo "$GITHUB_TOKEN"
    return 0
  fi

  # Global secrets file (~/.harness/secrets/github.credentials)
  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local global_token
    global_token=$(read_kv "$GLOBAL_CRED_FILE" "token")
    if [ -n "$global_token" ]; then
      echo "$global_token"
      return 0
    fi
  fi

  if [ -f "$CRED_FILE" ]; then
    local file_token
    file_token=$(read_kv "$CRED_FILE" "token")
    if [ -n "$file_token" ]; then
      echo "$file_token"
      return 0
    fi
  fi

  return 1
}

# --- Detect the (owner, repo) for the current repo ---------------------------
# Resolves from `git remote get-url origin`. Required for nearly every API call.
resolve_repo_slug() {
  local origin_url
  origin_url=$(git -C "$REPO_ROOT" config --get remote.origin.url 2>/dev/null || echo "")
  if [ -z "$origin_url" ]; then
    echo "ERROR: No origin remote configured for $REPO_ROOT" >&2
    return 1
  fi

  # Forms supported:
  #   git@host:owner/repo.git
  #   https://host/owner/repo.git
  #   https://host/owner/repo
  python3 - <<PYEOF "$origin_url"
import re, sys
url = sys.argv[1].strip()
m = re.match(r'^git@[^:]+:([^/]+)/(.+?)(?:\.git)?$', url) or \
    re.match(r'^https?://[^/]+/([^/]+)/(.+?)(?:\.git)?/?$', url)
if not m:
    sys.stderr.write(f"ERROR: cannot parse owner/repo from origin url: {url}\n")
    sys.exit(1)
print(f"{m.group(1)}/{m.group(2)}")
PYEOF
}

# --- Detect the current branch -----------------------------------------------
current_branch() {
  git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null
}

# --- Resolve auth for API calls ---------------------------------------------
# CURL_AUTH_ARGS as an array — symmetric with confluence.sh — so we can extend
# to other auth schemes in future without string juggling.
CURL_AUTH_ARGS=()

init_auth() {
  local token
  if ! token=$(resolve_token); then
    echo "ERROR: No GitHub credentials found." >&2
    echo "" >&2
    echo "To set up credentials, run one of:" >&2
    echo "  bash scripts/agent/github.sh setup        # interactive (recommended)" >&2
    echo "  export GITHUB_TOKEN=<your-pat>            # env var for this session" >&2
    echo "  export GITHUB_BASE_URL=<base-url>         # e.g., https://github.fkinternal.com" >&2
    exit 1
  fi

  CURL_AUTH_ARGS=(
    -H "Authorization: token ${token}"
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28"
  )
}

# --- Command: setup (interactive) -------------------------------------------
do_setup() {
  echo "━━━ GitHub Credential Setup ━━━"
  echo ""

  # Base URL — use the resolved default silently. Override with GITHUB_BASE_URL
  # env var if you need a non-default instance for this setup run.
  local current_url="${GITHUB_BASE_URL%/}"

  if [ -z "$current_url" ]; then
    echo "ERROR: Base URL is required (set GITHUB_BASE_URL or update DEFAULT_GITHUB_URL in github.sh)." >&2
    exit 1
  fi
  echo "Using base URL: $current_url"

  # Check global secrets file first — reuse if present
  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local global_token global_url
    global_token=$(read_kv "$GLOBAL_CRED_FILE" "token")
    global_url=$(read_kv "$GLOBAL_CRED_FILE" "base-url")
    if [ -n "$global_token" ]; then
      echo ""
      echo "Found existing global GitHub credentials (~/.harness/secrets/github.credentials)."
      [ -n "$global_url" ] && echo "  Base URL: $global_url"
      read -rp "Reuse these? [Y/n]: " reuse_global
      if [[ ! "$reuse_global" =~ ^[Nn] ]]; then
        local test_api_url
        case "$current_url" in
          https://github.com|http://github.com) test_api_url="https://api.github.com" ;;
          *) test_api_url="${current_url}/api/v3" ;;
        esac
        local test_response
        test_response=$(curl -sf --max-time 10 \
          -H "Authorization: token ${global_token}" \
          -H "Accept: application/vnd.github+json" \
          "${test_api_url}/user" 2>/dev/null || echo "")
        if [ -n "$test_response" ]; then
          local login
          login=$(echo "$test_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('login','unknown'))" 2>/dev/null)
          echo "Authenticated as: $login"
          {
            echo "# GitHub credentials (reused from global ~/.harness/secrets/github.credentials)"
            echo "base-url: ${current_url}"
            echo "token: ${global_token}"
          } > "$CRED_FILE"
          chmod 600 "$CRED_FILE"
          echo "Saved to .github-credentials (gitignored, 600 perms)."
          exit 0
        else
          echo "Global token didn't work — entering manual setup."
        fi
      fi
    fi
  fi

  # Token
  echo ""
  echo "Generate a Personal Access Token (classic) with scopes: repo, read:org"
  echo "  GHES: ${current_url}/settings/tokens"
  echo "  github.com: https://github.com/settings/tokens"
  echo ""
  read -rsp "Paste your token (hidden): " input_token
  echo ""

  if [ -z "$input_token" ]; then
    echo "ERROR: Token is required." >&2
    exit 1
  fi

  # Test the credentials against the API
  local api_url
  case "$current_url" in
    https://github.com|http://github.com) api_url="https://api.github.com" ;;
    *) api_url="${current_url}/api/v3" ;;
  esac

  echo "Testing connection to $api_url ..."
  local test_response
  test_response=$(curl -sf --max-time 10 \
    -H "Authorization: token ${input_token}" \
    -H "Accept: application/vnd.github+json" \
    "${api_url}/user" 2>/dev/null || echo "")

  if [ -z "$test_response" ]; then
    echo "ERROR: Authentication failed. Check the URL and token." >&2
    exit 1
  fi

  local login
  login=$(echo "$test_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('login','unknown'))" 2>/dev/null || echo "unknown")
  echo "Authenticated as: $login"

  # Save to global secrets file (persists across repos and sessions)
  mkdir -p "$(dirname "$GLOBAL_CRED_FILE")"
  chmod 700 "$(dirname "$GLOBAL_CRED_FILE")"
  {
    echo "# GitHub credentials for harness pipeline"
    echo "# Generated by: github.sh setup — do NOT commit this file"
    echo "base-url: ${current_url}"
    echo "token: ${input_token}"
  } > "$GLOBAL_CRED_FILE"
  chmod 600 "$GLOBAL_CRED_FILE"
  echo "Saved to ~/.harness/secrets/github.credentials (global, 600 perms)."

  # Save to per-repo credential file
  {
    echo "# GitHub credentials for harness pipeline"
    echo "# This file is gitignored — do NOT commit it"
    echo "# Generated by: github.sh setup"
    echo "base-url: ${current_url}"
    echo "token: ${input_token}"
  } > "$CRED_FILE"
  chmod 600 "$CRED_FILE"

  # Ensure .gitignore has the credential file
  if [ -f "${REPO_ROOT}/.gitignore" ]; then
    if ! grep -qF ".github-credentials" "${REPO_ROOT}/.gitignore"; then
      echo ".github-credentials" >> "${REPO_ROOT}/.gitignore"
      echo "Added .github-credentials to .gitignore"
    fi
  else
    echo ".github-credentials" > "${REPO_ROOT}/.gitignore"
    echo "Created .gitignore with .github-credentials"
  fi

  echo ""
  echo "Setup complete. Credentials saved to .github-credentials (gitignored, 0600)."
  exit 0
}

# --- Command: test-auth ------------------------------------------------------
do_test_auth() {
  local response
  response=$(curl -sf --max-time 10 \
    "${CURL_AUTH_ARGS[@]}" \
    "${GITHUB_API_URL}/user" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "AUTH_FAILED: Could not authenticate against ${GITHUB_API_URL}" >&2
    exit 1
  fi

  local login
  login=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('login','unknown'))" 2>/dev/null || echo "unknown")
  echo "AUTH_OK:${login} (api=${GITHUB_API_URL})"
}

# --- Command: push-branch ----------------------------------------------------
# Pushes the current branch to <remote> with -u so the upstream is tracked.
# Uses git's normal credential mechanism for the push (SSH or HTTPS+token).
# We do NOT inject the PAT into the push URL — that risks leaving credentials
# in `git config remote.origin.url` for the next user to read.
do_push_branch() {
  local remote="${1:-origin}"
  local branch
  branch=$(current_branch)
  if [ -z "$branch" ] || [ "$branch" = "HEAD" ]; then
    echo "ERROR: Detached HEAD — checkout a branch before pushing." >&2
    exit 1
  fi

  echo "Pushing ${branch} to ${remote} ..." >&2
  git -C "$REPO_ROOT" push -u "$remote" "$branch"
  echo "PUSHED:${remote}:${branch}"
}

# --- Command: get-pr-for-branch ---------------------------------------------
# Emits the PR number (if any) for the given branch, or empty string.
do_get_pr_for_branch() {
  local branch="${1:-$(current_branch)}"
  if [ -z "$branch" ]; then
    echo "ERROR: Could not determine branch." >&2
    exit 1
  fi

  local slug owner
  slug=$(resolve_repo_slug) || exit 1
  owner="${slug%%/*}"

  local response
  response=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${GITHUB_API_URL}/repos/${slug}/pulls?state=open&head=${owner}:${branch}&per_page=1" \
    2>/dev/null || echo "[]")

  echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data[0]['number'] if data else '')
"
}

# --- Command: create-pr ------------------------------------------------------
# Creates a PR from the current branch to <base-branch> (default: repo's
# default branch). Body is read from a markdown file (used as-is — GitHub
# renders markdown natively in PR bodies).
do_create_pr() {
  local title="$1"
  local body_md_file="$2"
  local base_branch="${3:-}"

  if [ ! -f "$body_md_file" ]; then
    echo "ERROR: Body file not found: $body_md_file" >&2
    exit 1
  fi

  local slug
  slug=$(resolve_repo_slug) || exit 1

  # Resolve default branch if base wasn't provided.
  if [ -z "$base_branch" ]; then
    base_branch=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
      "${GITHUB_API_URL}/repos/${slug}" \
      | python3 -c "import sys,json; print(json.load(sys.stdin).get('default_branch','main'))" 2>/dev/null || echo "main")
  fi

  local head_branch
  head_branch=$(current_branch)
  if [ -z "$head_branch" ] || [ "$head_branch" = "HEAD" ]; then
    echo "ERROR: Detached HEAD — checkout a branch before creating a PR." >&2
    exit 1
  fi

  if [ "$head_branch" = "$base_branch" ]; then
    echo "ERROR: head branch (${head_branch}) is the same as base (${base_branch})." >&2
    exit 1
  fi

  local payload
  payload=$(BODY_FILE="$body_md_file" TITLE="$title" HEAD="$head_branch" BASE="$base_branch" \
    python3 -c '
import json, os
with open(os.environ["BODY_FILE"], "r", encoding="utf-8") as f:
    body = f.read()
print(json.dumps({
    "title": os.environ["TITLE"],
    "head":  os.environ["HEAD"],
    "base":  os.environ["BASE"],
    "body":  body,
}))
')

  local response
  response=$(curl -sf -X POST "${CURL_AUTH_ARGS[@]}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${GITHUB_API_URL}/repos/${slug}/pulls" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Failed to create PR for ${slug} ${head_branch} → ${base_branch}" >&2
    exit 1
  fi

  local pr_number pr_url
  pr_number=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('number',''))" 2>/dev/null)
  pr_url=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('html_url',''))" 2>/dev/null)

  if [ -z "$pr_number" ]; then
    echo "ERROR: PR creation succeeded but response missing 'number'" >&2
    echo "$response" >&2
    exit 1
  fi

  echo "PR_CREATED:${pr_number}:${pr_url}"
}

# --- Command: ensure-pr ------------------------------------------------------
# If a PR already exists for the current branch, update its body; otherwise
# push + create.
do_ensure_pr() {
  local title="$1"
  local body_md_file="$2"
  local base_branch="${3:-}"

  if [ ! -f "$body_md_file" ]; then
    echo "ERROR: Body file not found: $body_md_file" >&2
    exit 1
  fi

  local existing
  existing=$(do_get_pr_for_branch)

  if [ -z "$existing" ]; then
    do_push_branch origin >&2
    do_create_pr "$title" "$body_md_file" "$base_branch"
    return
  fi

  local slug
  slug=$(resolve_repo_slug) || exit 1

  local payload
  payload=$(BODY_FILE="$body_md_file" TITLE="$title" \
    python3 -c '
import json, os
with open(os.environ["BODY_FILE"], "r", encoding="utf-8") as f:
    body = f.read()
print(json.dumps({"title": os.environ["TITLE"], "body": body}))
')

  local response
  response=$(curl -sf -X PATCH "${CURL_AUTH_ARGS[@]}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${GITHUB_API_URL}/repos/${slug}/pulls/${existing}" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Failed to update PR #${existing}" >&2
    exit 1
  fi

  local pr_url
  pr_url=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('html_url',''))" 2>/dev/null)
  echo "PR_UPDATED:${existing}:${pr_url}"
}

# --- Command: get-pr-comments -----------------------------------------------
# Returns three categories merged:
#   - Issue comments         (general PR conversation)
#   - Review comments        (line-level inline comments)
#   - Reviews                (the body field of submitted reviews)
# Each line uses the same shape as confluence.sh `get-comments`:
#   COMMENT:<id>:<author>:<created-at>:<kind>:<path-or-empty>:<body-text>
# kinds: issue | review | review_body
do_get_pr_comments() {
  local pr_number="$1"
  if [ -z "$pr_number" ]; then
    echo "ERROR: pr-number required" >&2
    exit 1
  fi

  local slug
  slug=$(resolve_repo_slug) || exit 1

  local issue_resp review_resp reviews_resp
  issue_resp=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${GITHUB_API_URL}/repos/${slug}/issues/${pr_number}/comments?per_page=100" 2>/dev/null || echo "[]")
  review_resp=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${GITHUB_API_URL}/repos/${slug}/pulls/${pr_number}/comments?per_page=100" 2>/dev/null || echo "[]")
  reviews_resp=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${GITHUB_API_URL}/repos/${slug}/pulls/${pr_number}/reviews?per_page=100" 2>/dev/null || echo "[]")

  ISSUE_JSON="$issue_resp" REVIEW_JSON="$review_resp" REVIEWS_JSON="$reviews_resp" \
    python3 - <<'PYEOF'
import json, os, re

def line(kind, c, path=""):
    cid    = c.get("id", "")
    author = (c.get("user") or {}).get("login", "unknown")
    when   = c.get("created_at", "") or c.get("submitted_at", "")
    body   = (c.get("body") or "").replace("\n", " ").replace("\r", " ").strip()
    # Truncate noisy long bodies for one-line display; agent fetches full bodies via the API itself when it needs them.
    if len(body) > 500:
        body = body[:497] + "..."
    print(f"COMMENT:{cid}:{author}:{when}:{kind}:{path}:{body}")

issues  = json.loads(os.environ.get("ISSUE_JSON")  or "[]")
reviews = json.loads(os.environ.get("REVIEW_JSON") or "[]")
review_bodies = json.loads(os.environ.get("REVIEWS_JSON") or "[]")

if not issues and not reviews and not review_bodies:
    print("NO_COMMENTS")
else:
    for c in issues:
        line("issue", c)
    for c in reviews:
        line("review", c, c.get("path", "") or "")
    for r in review_bodies:
        # Skip empty review bodies (just APPROVED with no text).
        if (r.get("body") or "").strip():
            line("review_body", r)
PYEOF
}

# --- Command: check-approved -------------------------------------------------
# Exits 0 if the PR has at least one APPROVED review whose state was not
# subsequently invalidated by a CHANGES_REQUESTED. Logic:
#   walk reviews chronologically; per-author latest non-COMMENT state wins;
#   if any author's latest is APPROVED → approved, else not approved.
do_check_approved() {
  local pr_number="$1"
  if [ -z "$pr_number" ]; then
    echo "ERROR: pr-number required" >&2
    exit 1
  fi

  local slug
  slug=$(resolve_repo_slug) || exit 1

  local reviews
  reviews=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${GITHUB_API_URL}/repos/${slug}/pulls/${pr_number}/reviews?per_page=100" 2>/dev/null || echo "[]")

  local verdict
  verdict=$(REVIEWS="$reviews" python3 -c '
import json, os
reviews = json.loads(os.environ.get("REVIEWS") or "[]")
latest = {}
for r in sorted(reviews, key=lambda r: r.get("submitted_at") or ""):
    user = (r.get("user") or {}).get("login")
    state = r.get("state")
    if not user or state == "COMMENTED":
        continue
    latest[user] = state
approvers = [u for u, s in latest.items() if s == "APPROVED"]
blockers  = [u for u, s in latest.items() if s == "CHANGES_REQUESTED"]
if blockers:
    print("CHANGES_REQUESTED:" + ",".join(blockers))
elif approvers:
    print("APPROVED:" + ",".join(approvers))
else:
    print("NO_APPROVAL")
')

  echo "$verdict"
  case "$verdict" in
    APPROVED:*) exit 0 ;;
    *)          exit 1 ;;
  esac
}

# --- Command: comment-pr -----------------------------------------------------
# Adds an issue-level comment to the PR (general conversation, not inline).
do_comment_pr() {
  local pr_number="$1"
  local text="$2"
  if [ -z "$pr_number" ] || [ -z "$text" ]; then
    echo "ERROR: usage: comment-pr <pr-number> <text>" >&2
    exit 1
  fi

  local slug
  slug=$(resolve_repo_slug) || exit 1

  local payload
  payload=$(TEXT="$text" python3 -c 'import json,os; print(json.dumps({"body": os.environ["TEXT"]}))')

  local response
  response=$(curl -sf -X POST "${CURL_AUTH_ARGS[@]}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${GITHUB_API_URL}/repos/${slug}/issues/${pr_number}/comments" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Failed to post comment on PR #${pr_number}" >&2
    exit 1
  fi

  local cid
  cid=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
  echo "COMMENT_POSTED:${cid}"
}

# --- Command: resolve-pr -----------------------------------------------------
# Accepts a numeric PR number, or a URL like
#   https://github.fkinternal.com/owner/repo/pull/123
# and prints just the number.
do_resolve_pr() {
  local input="$1"
  if [ -z "$input" ]; then
    echo "ERROR: usage: resolve-pr <url-or-number>" >&2
    exit 1
  fi

  if [[ "$input" =~ ^[0-9]+$ ]]; then
    echo "$input"
    return
  fi

  local n
  n=$(echo "$input" | sed -nE 's|.*/pull/([0-9]+).*|\1|p')
  if [ -z "$n" ]; then
    echo "ERROR: Could not extract PR number from: $input" >&2
    exit 1
  fi
  echo "$n"
}

# --- Dispatch ----------------------------------------------------------------
COMMAND="${1:-}"
shift || true

case "$COMMAND" in
  setup)
    do_setup
    ;;
  test-auth)
    init_auth
    do_test_auth
    ;;
  push-branch)
    init_auth
    do_push_branch "${1:-origin}"
    ;;
  create-pr)
    init_auth
    [ $# -ge 2 ] || { echo "Usage: $0 create-pr <title> <body-md-file> [<base-branch>]" >&2; exit 1; }
    do_create_pr "$1" "$2" "${3:-}"
    ;;
  ensure-pr)
    init_auth
    [ $# -ge 2 ] || { echo "Usage: $0 ensure-pr <title> <body-md-file> [<base-branch>]" >&2; exit 1; }
    do_ensure_pr "$1" "$2" "${3:-}"
    ;;
  get-pr-for-branch)
    init_auth
    do_get_pr_for_branch "${1:-}"
    ;;
  get-pr-comments)
    init_auth
    [ $# -ge 1 ] || { echo "Usage: $0 get-pr-comments <pr-number>" >&2; exit 1; }
    do_get_pr_comments "$1"
    ;;
  check-approved)
    init_auth
    [ $# -ge 1 ] || { echo "Usage: $0 check-approved <pr-number>" >&2; exit 1; }
    do_check_approved "$1"
    ;;
  comment-pr)
    init_auth
    [ $# -ge 2 ] || { echo "Usage: $0 comment-pr <pr-number> <text>" >&2; exit 1; }
    do_comment_pr "$1" "$2"
    ;;
  resolve-pr)
    [ $# -ge 1 ] || { echo "Usage: $0 resolve-pr <url-or-number>" >&2; exit 1; }
    do_resolve_pr "$1"
    ;;
  *)
    echo "Usage: $0 {setup|test-auth|push-branch|create-pr|ensure-pr|get-pr-for-branch|get-pr-comments|check-approved|comment-pr|resolve-pr}" >&2
    exit 1
    ;;
esac
