#!/usr/bin/env bash
# jira.sh — Jira Cloud REST API v3 helper for harness pipeline
# API docs: https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/
#
# Usage:
#   jira.sh setup                                    — interactive credential setup
#   jira.sh test-auth                                — verify credentials work
#   jira.sh get-epic <epic-key>                      — get epic details + existing children
#   jira.sh create-epic <initiative-key> <title> <description> [labels...]  — create epic under initiative (auto-assigns to current user)
#   jira.sh create-story <epic-key> <title> <description> <story-points> [labels...]  — create story with points (auto-assigns)
#   jira.sh create-subtask <story-key> <title> <description>           — create sub-task (auto-assigns)
#   jira.sh update-issue <issue-key> <field> <value>                   — update a field on an issue
#   jira.sh transition <issue-key> <status>                            — move issue to status
#   jira.sh add-comment <issue-key> <comment-text>                     — add a comment
#   jira.sh get-issue <issue-key>                                      — get issue details
#   jira.sh link-issues <from-key> <to-key> <link-type>               — link two issues
#   jira.sh attach-file <issue-key> <file-path>                       — attach a file to an issue
#   jira.sh log-work <issue-key> <time-spent> [comment]               — log work (e.g., "2h 30m")
#   jira.sh resolve-key <url-or-key>                                   — extract issue key from URL or pass through
#
# Auth: Atlassian Cloud only — Basic auth with email + API token.
# Same token as Confluence (both use Atlassian Cloud at flipkart.atlassian.net).
#
# Auth resolution order (first match wins):
#   1. JIRA_TOKEN + JIRA_EMAIL env vars
#   2. ~/.harness/secrets/atlassian.credentials (global, persisted by setup — shared with Confluence)
#   3. .jira-credentials file in repo root (gitignored, created by 'setup')
#   4. .confluence-credentials file (fallback — same Atlassian token)
#
# SECURITY:
#   - .jira-credentials is gitignored (setup adds it)
#   - Tokens are NEVER stored in harness-state.md (which is committed)
#   - Credential file has 600 permissions

set -euo pipefail

# --- Paths -------------------------------------------------------------------
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
HARNESS_STATE="${REPO_ROOT}/harness-state.md"
CRED_FILE="${REPO_ROOT}/.jira-credentials"
GLOBAL_CRED_FILE="${HOME}/.harness/secrets/atlassian.credentials"

# --- Helpers: portable key-value extraction ----------------------------------
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

# --- Text → ADF converter (Jira v3 requires Atlassian Document Format) -------
# Converts plain text to ADF JSON. Splits on newlines into paragraphs.
text_to_adf() {
  local text="$1"
  python3 -c "
import json, sys
text = '''$text'''
paragraphs = []
for line in text.split('\n'):
    line = line.strip()
    if line:
        paragraphs.append({
            'type': 'paragraph',
            'content': [{'type': 'text', 'text': line}]
        })
    else:
        paragraphs.append({'type': 'paragraph', 'content': []})
doc = {'type': 'doc', 'version': 1, 'content': paragraphs if paragraphs else [{'type': 'paragraph', 'content': [{'type': 'text', 'text': ' '}]}]}
print(json.dumps(doc))
" 2>/dev/null
}

# --- Resolve current user's accountId (for assignee) -------------------------
# Caches the result so multiple create calls don't hit /myself repeatedly.
_CURRENT_ACCOUNT_ID=""
resolve_account_id() {
  if [ -n "$_CURRENT_ACCOUNT_ID" ]; then
    echo "$_CURRENT_ACCOUNT_ID"
    return 0
  fi
  local auth_header
  auth_header=$(build_auth_header)
  local response
  response=$(curl -sf --max-time 10 -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/myself" 2>/dev/null || echo "")
  _CURRENT_ACCOUNT_ID=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accountId',''))" 2>/dev/null || echo "")
  if [ -z "$_CURRENT_ACCOUNT_ID" ]; then
    echo "" # non-fatal — stories will be unassigned
    return 1
  fi
  echo "$_CURRENT_ACCOUNT_ID"
}

# --- Detect story points custom field ----------------------------------------
# Common IDs: customfield_10016 (Cloud next-gen), customfield_10028, etc.
# We detect it once and cache.
_STORY_POINTS_FIELD=""
resolve_story_points_field() {
  if [ -n "$_STORY_POINTS_FIELD" ]; then
    echo "$_STORY_POINTS_FIELD"
    return 0
  fi
  local auth_header
  auth_header=$(build_auth_header)
  _STORY_POINTS_FIELD=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/field" 2>/dev/null | \
    python3 -c "
import sys, json
fields = json.load(sys.stdin)
for f in fields:
    if f.get('name') == 'Story Points' or f.get('name') == 'Story point estimate':
        print(f['id'])
        break
" 2>/dev/null || echo "customfield_10016")
  echo "$_STORY_POINTS_FIELD"
}

# --- Load base URL -----------------------------------------------------------
# Default base URL for Flipkart Atlassian Cloud
DEFAULT_JIRA_URL="https://flipkart.atlassian.net"

JIRA_BASE_URL="${JIRA_BASE_URL:-}"

if [ -z "$JIRA_BASE_URL" ] && [ -f "$HARNESS_STATE" ]; then
  JIRA_BASE_URL=$(read_kv "$HARNESS_STATE" "jira-base-url")
fi

if [ -z "$JIRA_BASE_URL" ] && [ -f "$GLOBAL_CRED_FILE" ]; then
  JIRA_BASE_URL=$(read_kv "$GLOBAL_CRED_FILE" "jira-base-url")
fi

if [ -z "$JIRA_BASE_URL" ] && [ -f "$CRED_FILE" ]; then
  JIRA_BASE_URL=$(read_kv "$CRED_FILE" "base-url")
fi

# Fall back to default if nothing found
JIRA_BASE_URL="${JIRA_BASE_URL:-$DEFAULT_JIRA_URL}"

# --- Load auth token ---------------------------------------------------------
resolve_token() {
  # 1. Environment variable
  if [ -n "${JIRA_TOKEN:-}" ]; then
    echo "$JIRA_TOKEN"
    return 0
  fi

  # 2. Global secrets file (~/.harness/secrets/atlassian.credentials)
  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local global_token
    global_token=$(read_kv "$GLOBAL_CRED_FILE" "token")
    if [ -n "$global_token" ]; then
      echo "$global_token"
      return 0
    fi
  fi

  # 3. Per-repo credential file
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

# --- Load email (Atlassian Cloud Basic auth: email + API token) ---------------
resolve_email() {
  if [ -n "${JIRA_EMAIL:-}" ]; then
    echo "$JIRA_EMAIL"
    return 0
  fi
  # Global secrets file
  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local global_email
    global_email=$(read_kv "$GLOBAL_CRED_FILE" "email")
    if [ -n "$global_email" ]; then
      echo "$global_email"
      return 0
    fi
  fi
  if [ -f "$CRED_FILE" ]; then
    local email
    email=$(read_kv "$CRED_FILE" "email")
    if [ -n "$email" ]; then
      echo "$email"
      return 0
    fi
  fi
  # Fallback: read from .confluence-credentials (same Atlassian account)
  local conf_cred="${REPO_ROOT}/.confluence-credentials"
  if [ -f "$conf_cred" ]; then
    local email
    email=$(read_kv "$conf_cred" "email")
    if [ -n "$email" ]; then
      echo "$email"
      return 0
    fi
  fi
  return 1
}

# --- Build auth header -------------------------------------------------------
# Atlassian Cloud only — Basic auth with email + API token.
build_auth_header() {
  local token
  if ! token=$(resolve_token); then
    # Fallback: try .confluence-credentials (same Atlassian token)
    local conf_cred="${REPO_ROOT}/.confluence-credentials"
    if [ -f "$conf_cred" ]; then
      token=$(read_kv "$conf_cred" "token")
    fi
    if [ -z "$token" ]; then
      echo "ERROR: No Jira credentials found." >&2
      echo "Run: bash scripts/agent/jira.sh setup" >&2
      echo "(Or reuse Confluence credentials — same Atlassian API token)" >&2
      exit 1
    fi
  fi

  local email
  if ! email=$(resolve_email 2>/dev/null) || [ -z "$email" ]; then
    echo "ERROR: No email found for Atlassian Cloud auth." >&2
    echo "Run: bash scripts/agent/jira.sh setup" >&2
    exit 1
  fi

  # Atlassian Cloud: Basic auth with email:token
  echo "Authorization: Basic $(echo -n "${email}:${token}" | base64)"
}

# --- Command: setup ----------------------------------------------------------
do_setup() {
  echo "━━━ Jira Credential Setup (Atlassian Cloud) ━━━"
  echo ""

  local current_url="${JIRA_BASE_URL:-$DEFAULT_JIRA_URL}"
  current_url="${current_url%/}"
  echo "Using base URL: $current_url"

  # Check global secrets file first — reuse if present (avoids re-prompting across repos/sessions)
  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local global_email global_token
    global_email=$(read_kv "$GLOBAL_CRED_FILE" "email")
    global_token=$(read_kv "$GLOBAL_CRED_FILE" "token")
    if [ -n "$global_email" ] && [ -n "$global_token" ]; then
      echo ""
      echo "Found existing global Atlassian credentials (~/.harness/secrets/atlassian.credentials)."
      echo "  Email: $global_email"
      read -rp "Reuse these? [Y/n]: " reuse_global
      if [[ ! "$reuse_global" =~ ^[Nn] ]]; then
        local auth_header="Authorization: Basic $(echo -n "${global_email}:${global_token}" | base64)"
        local test_response
        test_response=$(curl -sf --max-time 10 -H "$auth_header" \
          "${current_url}/rest/api/3/myself" 2>/dev/null || echo "")
        if [ -n "$test_response" ]; then
          local display_name
          display_name=$(echo "$test_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('displayName','unknown'))" 2>/dev/null)
          echo "Authenticated as: $display_name"
          cat > "$CRED_FILE" <<CREDEOF
# Jira credentials (reused from global ~/.harness/secrets/atlassian.credentials)
base-url: ${current_url}
email: ${global_email}
token: ${global_token}
CREDEOF
          chmod 600 "$CRED_FILE"
          echo "Saved to .jira-credentials (gitignored, 600 perms)."
          exit 0
        else
          echo "Global token didn't work — entering manual setup."
        fi
      fi
    fi
  fi

  # Check if .confluence-credentials exists — reuse the same Atlassian token
  local conf_cred="${REPO_ROOT}/.confluence-credentials"
  if [ -f "$conf_cred" ]; then
    local conf_email conf_token
    conf_email=$(read_kv "$conf_cred" "email")
    conf_token=$(read_kv "$conf_cred" "token")
    if [ -n "$conf_email" ] && [ -n "$conf_token" ]; then
      echo ""
      echo "Found existing Confluence credentials (same Atlassian account)."
      echo "  Email: $conf_email"
      read -rp "Reuse these for Jira? [Y/n]: " reuse_choice
      if [[ ! "$reuse_choice" =~ ^[Nn] ]]; then
        # Test with Confluence creds
        local auth_header="Authorization: Basic $(echo -n "${conf_email}:${conf_token}" | base64)"
        local test_response
        test_response=$(curl -sf --max-time 10 -H "$auth_header" \
          "${current_url}/rest/api/3/myself" 2>/dev/null || echo "")
        if [ -n "$test_response" ]; then
          local display_name
          display_name=$(echo "$test_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('displayName','unknown'))" 2>/dev/null)
          echo "Authenticated as: $display_name (reusing Confluence token)"
          cat > "$CRED_FILE" <<CREDEOF
# Jira credentials for harness pipeline (reused from Confluence)
base-url: ${current_url}
email: ${conf_email}
token: ${conf_token}
CREDEOF
          chmod 600 "$CRED_FILE"
          echo "Saved to .jira-credentials (gitignored, 600 perms)."
          exit 0
        else
          echo "Confluence token didn't work for Jira — entering manual setup."
        fi
      fi
    fi
  fi

  # Manual setup — Atlassian Cloud: email + API token
  echo ""
  echo "Atlassian Cloud uses Basic auth: email + API token."
  echo "Generate a token at: https://id.atlassian.com/manage-profile/security/api-tokens"
  echo "(Same token works for both Confluence and Jira)"
  echo ""

  read -rp "Atlassian email (e.g., you@flipkart.com): " input_email
  read -rsp "API token (hidden): " input_token
  echo ""

  if [ -z "$input_email" ] || [ -z "$input_token" ]; then
    echo "ERROR: Email and token are both required." >&2
    exit 1
  fi

  # Test connection
  echo "Testing connection..."
  local auth_header="Authorization: Basic $(echo -n "${input_email}:${input_token}" | base64)"

  local test_response
  test_response=$(curl -sf --max-time 10 \
    -H "$auth_header" \
    "${current_url}/rest/api/3/myself" 2>/dev/null || echo "")

  if [ -z "$test_response" ]; then
    echo "ERROR: Authentication failed. Check your email and token." >&2
    exit 1
  fi

  local display_name
  display_name=$(echo "$test_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('displayName','unknown'))" 2>/dev/null || echo "unknown")
  echo "Authenticated as: $display_name"

  # Save to global secrets file (persists across repos and sessions)
  mkdir -p "$(dirname "$GLOBAL_CRED_FILE")"
  chmod 700 "$(dirname "$GLOBAL_CRED_FILE")"
  {
    echo "# Atlassian credentials for harness pipeline"
    echo "# Shared between Confluence and Jira (same API token)"
    echo "# Generated by: jira.sh setup — do NOT commit this file"
    echo "confluence-base-url: ${current_url%/}/wiki"
    echo "jira-base-url: ${current_url}"
    echo "email: ${input_email}"
    echo "token: ${input_token}"
  } > "$GLOBAL_CRED_FILE"
  chmod 600 "$GLOBAL_CRED_FILE"
  echo "Saved to ~/.harness/secrets/atlassian.credentials (global, 600 perms)."

  cat > "$CRED_FILE" <<CREDEOF
# Jira credentials for harness pipeline
base-url: ${current_url}
email: ${input_email}
token: ${input_token}
CREDEOF
  chmod 600 "$CRED_FILE"

  # Ensure .gitignore
  if [ -f "${REPO_ROOT}/.gitignore" ]; then
    grep -qF ".jira-credentials" "${REPO_ROOT}/.gitignore" 2>/dev/null || \
      echo ".jira-credentials" >> "${REPO_ROOT}/.gitignore"
  fi

  echo ""
  echo "Setup complete. Credentials saved to .jira-credentials (gitignored, 600 perms)."
  echo "Tip: This is the same Atlassian API token used for Confluence."
  exit 0
}

# --- Command: test-auth ------------------------------------------------------
do_test_auth() {
  local auth_header
  auth_header=$(build_auth_header)

  if [ -z "$JIRA_BASE_URL" ]; then
    echo "NO_URL: Jira base URL not configured." >&2
    exit 1
  fi

  local response
  response=$(curl -sf --max-time 10 \
    -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/myself" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "AUTH_FAILED" >&2
    exit 1
  fi

  local display_name
  display_name=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('displayName','unknown'))" 2>/dev/null || echo "unknown")
  echo "AUTH_OK:${display_name}"
}

# --- Command: get-epic -------------------------------------------------------
do_get_epic() {
  local epic_key="$1"
  local auth_header
  auth_header=$(build_auth_header)

  # Get epic details
  local epic_response
  epic_response=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${epic_key}" 2>/dev/null || echo "")

  if [ -z "$epic_response" ]; then
    echo "ERROR: Could not fetch epic ${epic_key}" >&2
    exit 1
  fi

  local epic_summary project_key
  epic_summary=$(echo "$epic_response" | python3 -c "import sys,json; print(json.load(sys.stdin)['fields']['summary'])" 2>/dev/null)
  project_key=$(echo "$epic_response" | python3 -c "import sys,json; print(json.load(sys.stdin)['fields']['project']['key'])" 2>/dev/null)

  echo "EPIC:${epic_key}:${project_key}:${epic_summary}"

  # Get existing children (stories under this epic)
  local children
  children=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/search?jql=%22Epic+Link%22=${epic_key}&fields=key,summary,status,issuetype&maxResults=50" 2>/dev/null || echo "")

  if [ -n "$children" ]; then
    python3 -c "
import sys, json
data = json.loads(sys.stdin.read())
for issue in data.get('issues', []):
    key = issue['key']
    summary = issue['fields']['summary']
    status = issue['fields']['status']['name']
    itype = issue['fields']['issuetype']['name']
    print(f'CHILD:{key}:{itype}:{status}:{summary}')
" <<< "$children" 2>/dev/null
  fi
}

# --- Command: create-epic ----------------------------------------------------
do_create_epic() {
  local initiative_key="$1"
  local title="$2"
  local description="$3"
  shift 3
  local labels=("$@")

  local auth_header
  auth_header=$(build_auth_header)

  # Get project key from the initiative
  local init_response init_http
  init_response=$(curl -s -w "\n__HTTP_CODE__:%{http_code}" -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${initiative_key}" 2>/dev/null || echo "")
  init_http=$(echo "$init_response" | grep -o '__HTTP_CODE__:[0-9]*' | cut -d: -f2)
  init_response=$(echo "$init_response" | sed '/__HTTP_CODE__:/d')

  local project_key
  project_key=$(echo "$init_response" | python3 -c "import sys,json; print(json.load(sys.stdin)['fields']['project']['key'])" 2>/dev/null)

  if [ -z "$project_key" ]; then
    echo "ERROR: Could not resolve project key from initiative ${initiative_key} (HTTP ${init_http:-unknown})" >&2
    echo "$init_response" >&2
    exit 1
  fi

# Discover Epic issue type ID for this project (avoids 'name' mismatch on company-managed projects)
  local epic_type_id
  epic_type_id=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/createmeta?projectKeys=${project_key}&expand=projects.issuetypes" \
    2>/dev/null | python3 -c "
import sys, json
d = json.load(sys.stdin)
for p in d.get('projects', []):
    for t in p.get('issuetypes', []):
        if t.get('name', '').lower() == 'epic':
            print(t['id'])
            raise SystemExit(0)
" 2>/dev/null || echo "")
  # Fall back to 'name' if ID detection fails
  local issuetype_field
  if [ -n "$epic_type_id" ]; then
    issuetype_field="{\"id\": \"${epic_type_id}\"}"
  else
    issuetype_field="{\"name\": \"Epic\"}"
  fi

  # Discover required custom fields for Epic in this project; build safe defaults
  local required_fields_json
  required_fields_json=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/createmeta?projectKeys=${project_key}&issuetypeIds=${epic_type_id}&expand=projects.issuetypes.fields" \
    2>/dev/null | python3 -c "
import sys, json
d = json.load(sys.stdin)
result = {}
for p in d.get('projects', []):
    for it in p.get('issuetypes', []):
        for fname, fdata in it.get('fields', {}).items():
            if not fdata.get('required', False):
                continue
            if fname in ('issuetype', 'project', 'summary'):
                continue
            avs = fdata.get('allowedValues', [])
            if avs:
                # Pick first allowed value as default
                av = avs[0]
                if fdata.get('schema', {}).get('type') == 'array':
                    result[fname] = [{'id': av['id']}]
                else:
                    result[fname] = {'id': av['id']}
            elif fdata.get('schema', {}).get('type') == 'priority':
                result[fname] = {'name': 'P2'}
print(json.dumps(result))
" 2>/dev/null || echo "{}")

  # Detect Epic Name custom field (optional but commonly required)
  local epic_name_field
  epic_name_field=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/field" 2>/dev/null | \
    python3 -c "
import sys, json
for f in json.load(sys.stdin):
    if f.get('name') == 'Epic Name':
        print(f['id'])
        break
" 2>/dev/null || echo "customfield_10011")

  # Build labels JSON array
  local labels_json="[]"
  if [ ${#labels[@]} -gt 0 ]; then
    labels_json=$(python3 -c "import json; print(json.dumps([$(printf '"%s",' "${labels[@]}" | sed 's/,$//')]))")
  fi

  local desc_adf
  desc_adf=$(text_to_adf "$description")

  # Auto-assign to current user
  local account_id
  account_id=$(resolve_account_id 2>/dev/null || echo "")

  local payload
  payload=$(DESC_ADF="$desc_adf" ACCOUNT_ID="$account_id" \
    REQUIRED_FIELDS="$required_fields_json" ISSUETYPE="$issuetype_field" python3 -c "
import json, os
required = json.loads(os.environ.get('REQUIRED_FIELDS', '{}'))
fields = {
    'project': {'key': '$project_key'},
    'summary': $(python3 -c "import json; print(json.dumps('$title'))"),
    'description': json.loads(os.environ['DESC_ADF']),
    'issuetype': json.loads(os.environ['ISSUETYPE']),
    '$epic_name_field': $(python3 -c "import json; print(json.dumps('$title'))"),
    'labels': $labels_json,
    'customfield_10018': '$initiative_key',
}
# Merge auto-discovered required fields (do not overwrite explicitly set fields)
for k, v in required.items():
    if k not in fields:
        fields[k] = v
# Priority default
if 'priority' not in fields:
    fields['priority'] = {'name': 'P2'}
aid = os.environ.get('ACCOUNT_ID', '')
if aid:
    fields['assignee'] = {'accountId': aid}
print(json.dumps({'fields': fields}))
")

  local response http_code
  response=$(curl -s -w "\n__HTTP_CODE__:%{http_code}" -X POST \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${JIRA_BASE_URL}/rest/api/3/issue" 2>/dev/null || echo "")
  http_code=$(echo "$response" | grep -o '__HTTP_CODE__:[0-9]*' | cut -d: -f2)
  response=$(echo "$response" | sed '/__HTTP_CODE__:/d')

  local epic_key
  epic_key=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['key'])" 2>/dev/null)

  if [ -z "$epic_key" ]; then
    echo "ERROR: Failed to create epic (HTTP ${http_code:-unknown})" >&2
    echo "$response" >&2
    exit 1
  fi

  echo "EPIC_CREATED:${epic_key}:${JIRA_BASE_URL}/browse/${epic_key}"
}
# --- Command: create-story ---------------------------------------------------
do_create_story() {
  local epic_key="$1"
  local title="$2"
  local description="$3"
  local story_points="${4:-}"
  shift 4 2>/dev/null || shift 3
  local labels=("$@")

  local auth_header
  auth_header=$(build_auth_header)

  # Get project key and epic link field from the epic
  local epic_response epic_http
  epic_response=$(curl -s -w "\n__HTTP_CODE__:%{http_code}" -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${epic_key}" 2>/dev/null || echo "")
  epic_http=$(echo "$epic_response" | grep -o '__HTTP_CODE__:[0-9]*' | cut -d: -f2)
  epic_response=$(echo "$epic_response" | sed '/__HTTP_CODE__:/d')

  local project_key
  project_key=$(echo "$epic_response" | python3 -c "import sys,json; print(json.load(sys.stdin)['fields']['project']['key'])" 2>/dev/null)

  if [ -z "$project_key" ]; then
    echo "ERROR: Could not resolve project key from epic ${epic_key} (HTTP ${epic_http:-unknown})" >&2
    echo "$epic_response" >&2
    exit 1
  fi

  # Build labels JSON array
  local labels_json="[]"
  if [ ${#labels[@]} -gt 0 ]; then
    labels_json=$(python3 -c "import json; print(json.dumps([$(printf '"%s",' "${labels[@]}" | sed 's/,$//')]))")
  fi

  # Create story with Epic Link
  # Note: "Epic Link" custom field ID varies per Jira instance
  # Common IDs: customfield_10014 (Cloud), customfield_10008 (Server)
  # We try to detect it
  local epic_link_field
  epic_link_field=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/field" 2>/dev/null | \
    python3 -c "
import sys, json
fields = json.load(sys.stdin)
for f in fields:
    if f.get('name') == 'Epic Link':
        print(f['id'])
        break
" 2>/dev/null || echo "customfield_10014")

  local desc_adf
  desc_adf=$(text_to_adf "$description")

  # Auto-assign to current user + story points
  local account_id
  account_id=$(resolve_account_id 2>/dev/null || echo "")
  local sp_field
  sp_field=$(resolve_story_points_field 2>/dev/null || echo "customfield_10016")

  local payload
  payload=$(DESC_ADF="$desc_adf" ACCOUNT_ID="$account_id" SP_FIELD="$sp_field" SP_VAL="$story_points" python3 -c "
import json, os
fields = {
    'project': {'key': '$project_key'},
    'summary': $(python3 -c "import json; print(json.dumps('$title'))"),
    'description': json.loads(os.environ['DESC_ADF']),
    'issuetype': {'name': 'Story'},
    '$epic_link_field': '$epic_key',
    'labels': $labels_json
}
aid = os.environ.get('ACCOUNT_ID', '')
if aid:
    fields['assignee'] = {'accountId': aid}
sp_val = os.environ.get('SP_VAL', '')
sp_field = os.environ.get('SP_FIELD', '')
if sp_val and sp_field:
    try:
        fields[sp_field] = float(sp_val)
    except ValueError:
        pass
print(json.dumps({'fields': fields}))
")

  local response http_code
  response=$(curl -s -w "\n__HTTP_CODE__:%{http_code}" -X POST \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${JIRA_BASE_URL}/rest/api/3/issue" 2>/dev/null || echo "")
  http_code=$(echo "$response" | grep -o '__HTTP_CODE__:[0-9]*' | cut -d: -f2)
  response=$(echo "$response" | sed '/__HTTP_CODE__:/d')

  local story_key
  story_key=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['key'])" 2>/dev/null)

  if [ -z "$story_key" ]; then
    echo "ERROR: Failed to create story (HTTP ${http_code:-unknown})" >&2
    echo "$response" >&2
    exit 1
  fi

  echo "STORY_CREATED:${story_key}:${JIRA_BASE_URL}/browse/${story_key}"
}

# --- Command: create-subtask -------------------------------------------------
do_create_subtask() {
  local parent_key="$1"
  local title="$2"
  local description="$3"

  local auth_header
  auth_header=$(build_auth_header)

  # Get project key from parent
  local parent_response
  parent_response=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${parent_key}" 2>/dev/null || echo "")

  local project_key
  project_key=$(echo "$parent_response" | python3 -c "import sys,json; print(json.load(sys.stdin)['fields']['project']['key'])" 2>/dev/null)

  local desc_adf
  desc_adf=$(text_to_adf "$description")

  # Auto-assign to current user
  local account_id
  account_id=$(resolve_account_id 2>/dev/null || echo "")

  local payload
  payload=$(DESC_ADF="$desc_adf" ACCOUNT_ID="$account_id" python3 -c "
import json, os
fields = {
    'project': {'key': '$project_key'},
    'parent': {'key': '$parent_key'},
    'summary': $(python3 -c "import json; print(json.dumps('$title'))"),
    'description': json.loads(os.environ['DESC_ADF']),
    'issuetype': {'name': 'Sub-task'}
}
aid = os.environ.get('ACCOUNT_ID', '')
if aid:
    fields['assignee'] = {'accountId': aid}
print(json.dumps({'fields': fields}))
")

  local response http_code
  response=$(curl -s -w "\n__HTTP_CODE__:%{http_code}" -X POST \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${JIRA_BASE_URL}/rest/api/3/issue" 2>/dev/null || echo "")
  http_code=$(echo "$response" | grep -o '__HTTP_CODE__:[0-9]*' | cut -d: -f2)
  response=$(echo "$response" | sed '/__HTTP_CODE__:/d')

  local subtask_key
  subtask_key=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['key'])" 2>/dev/null)

  if [ -z "$subtask_key" ]; then
    echo "ERROR: Failed to create subtask (HTTP ${http_code:-unknown})" >&2
    echo "$response" >&2
    exit 1
  fi

  echo "SUBTASK_CREATED:${subtask_key}:${JIRA_BASE_URL}/browse/${subtask_key}"
}

# --- Command: update-issue ---------------------------------------------------
do_update_issue() {
  local issue_key="$1"
  local field="$2"
  local value="$3"

  local auth_header
  auth_header=$(build_auth_header)

  local payload
  payload=$(python3 -c "
import json
update = {'fields': {'$field': $(python3 -c "import json; print(json.dumps('$value'))")}}
print(json.dumps(update))
")

  curl -sf -X PUT \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${issue_key}" > /dev/null 2>&1

  echo "UPDATED:${issue_key}:${field}"
}

# --- Command: transition -----------------------------------------------------
do_transition() {
  local issue_key="$1"
  local target_status="$2"

  local auth_header
  auth_header=$(build_auth_header)

  # Get available transitions
  local transitions
  transitions=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${issue_key}/transitions" 2>/dev/null)

  local transition_id
  transition_id=$(echo "$transitions" | python3 -c "
import sys, json
data = json.load(sys.stdin)
target = '$target_status'.lower()
for t in data.get('transitions', []):
    if t['to']['name'].lower() == target or t['name'].lower() == target:
        print(t['id'])
        break
" 2>/dev/null)

  if [ -z "$transition_id" ]; then
    echo "ERROR: No transition found to status '${target_status}' for ${issue_key}" >&2
    exit 1
  fi

  curl -sf -X POST \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "{\"transition\": {\"id\": \"${transition_id}\"}}" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${issue_key}/transitions" > /dev/null 2>&1

  echo "TRANSITIONED:${issue_key}:${target_status}"
}

# --- Command: add-comment ----------------------------------------------------
do_add_comment() {
  local issue_key="$1"
  local comment_text="$2"

  local auth_header
  auth_header=$(build_auth_header)

  local body_adf
  body_adf=$(text_to_adf "$comment_text")

  local payload
  payload=$(BODY_ADF="$body_adf" python3 -c "
import json, os
print(json.dumps({'body': json.loads(os.environ['BODY_ADF'])}))
")

  curl -sf -X POST \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${issue_key}/comment" > /dev/null 2>&1

  echo "COMMENT_ADDED:${issue_key}"
}

# --- Command: get-issue ------------------------------------------------------
do_get_issue() {
  local issue_key="$1"

  local auth_header
  auth_header=$(build_auth_header)

  local response
  response=$(curl -sf -H "$auth_header" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${issue_key}?fields=key,summary,status,issuetype,description,labels,subtasks" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Could not fetch issue ${issue_key}" >&2
    exit 1
  fi

  python3 -c "
import sys, json
d = json.load(sys.stdin)
f = d['fields']
print(f'ISSUE:{d[\"key\"]}:{f[\"issuetype\"][\"name\"]}:{f[\"status\"][\"name\"]}:{f[\"summary\"]}')
for st in f.get('subtasks', []):
    print(f'  SUBTASK:{st[\"key\"]}:{st[\"fields\"][\"status\"][\"name\"]}:{st[\"fields\"][\"summary\"]}')
" <<< "$response" 2>/dev/null
}

# --- Command: link-issues ----------------------------------------------------
do_link_issues() {
  local from_key="$1"
  local to_key="$2"
  local link_type="${3:-Relates}"

  local auth_header
  auth_header=$(build_auth_header)

  local payload
  payload=$(python3 -c "
import json
link = {
    'type': {'name': '$link_type'},
    'inwardIssue': {'key': '$from_key'},
    'outwardIssue': {'key': '$to_key'}
}
print(json.dumps(link))
")

  curl -sf -X POST \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${JIRA_BASE_URL}/rest/api/3/issueLink" > /dev/null 2>&1

  echo "LINKED:${from_key}→${to_key}:${link_type}"
}

# --- Command: attach-file ----------------------------------------------------
do_attach_file() {
  local issue_key="$1"
  local file_path="$2"

  if [ ! -f "$file_path" ]; then
    echo "ERROR: File not found: $file_path" >&2
    exit 1
  fi

  local auth_header
  auth_header=$(build_auth_header)

  local filename
  filename=$(basename "$file_path")

  local response
  response=$(curl -sf -X POST \
    -H "$auth_header" \
    -H "X-Atlassian-Token: no-check" \
    -F "file=@${file_path}" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${issue_key}/attachments" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Failed to attach file to ${issue_key}" >&2
    exit 1
  fi

  local attachment_id
  attachment_id=$(echo "$response" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['id'] if isinstance(data, list) and data else '')" 2>/dev/null)

  echo "ATTACHED:${issue_key}:${filename}:${attachment_id}"
}

# --- Command: log-work -------------------------------------------------------
do_log_work() {
  local issue_key="$1"
  local time_spent="$2"       # Jira time format: "2h 30m", "1d", "45m"
  local comment="${3:-}"

  local auth_header
  auth_header=$(build_auth_header)

  local payload
  if [ -n "$comment" ]; then
    local comment_adf
    comment_adf=$(text_to_adf "$comment")
    payload=$(COMMENT_ADF="$comment_adf" python3 -c "
import json, os
worklog = {'timeSpent': '$time_spent', 'comment': json.loads(os.environ['COMMENT_ADF'])}
print(json.dumps(worklog))
")
  else
    payload=$(python3 -c "import json; print(json.dumps({'timeSpent': '$time_spent'}))")
  fi

  local response
  response=$(curl -sf -X POST \
    -H "$auth_header" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${JIRA_BASE_URL}/rest/api/3/issue/${issue_key}/worklog" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Failed to log work on ${issue_key}" >&2
    exit 1
  fi

  echo "WORKLOG_ADDED:${issue_key}:${time_spent}"
}

# --- Command: resolve-key ----------------------------------------------------
do_resolve_key() {
  local input="$1"

  # If it's already a key (e.g., PROJ-123), pass through
  if echo "$input" | grep -qE '^[A-Z]+-[0-9]+$'; then
    echo "KEY:${input}"
    return 0
  fi

  # Extract from URL: https://jira.example.com/browse/PROJ-123
  local key
  key=$(echo "$input" | grep -oE '[A-Z]+-[0-9]+' | head -1)

  if [ -n "$key" ]; then
    echo "KEY:${key}"
    return 0
  fi

  echo "ERROR: Could not extract issue key from: $input" >&2
  exit 1
}

# --- Dispatch ----------------------------------------------------------------
COMMAND="${1:-}"
shift || true

case "$COMMAND" in
  setup)
    do_setup
    ;;
  test-auth)
    do_test_auth
    ;;
  get-epic)
    [ $# -ge 1 ] || { echo "Usage: $0 get-epic <epic-key>" >&2; exit 1; }
    do_get_epic "$1"
    ;;
  create-epic)
    [ $# -ge 3 ] || { echo "Usage: $0 create-epic <initiative-key> <title> <description> [labels...]" >&2; exit 1; }
    do_create_epic "$@"
    ;;
  create-story)
    [ $# -ge 3 ] || { echo "Usage: $0 create-story <epic-key> <title> <description> [story-points] [labels...]" >&2; exit 1; }
    do_create_story "$@"
    ;;
  create-subtask)
    [ $# -ge 3 ] || { echo "Usage: $0 create-subtask <story-key> <title> <description>" >&2; exit 1; }
    do_create_subtask "$1" "$2" "$3"
    ;;
  update-issue)
    [ $# -ge 3 ] || { echo "Usage: $0 update-issue <issue-key> <field> <value>" >&2; exit 1; }
    do_update_issue "$1" "$2" "$3"
    ;;
  transition)
    [ $# -ge 2 ] || { echo "Usage: $0 transition <issue-key> <status>" >&2; exit 1; }
    do_transition "$1" "$2"
    ;;
  add-comment)
    [ $# -ge 2 ] || { echo "Usage: $0 add-comment <issue-key> <comment-text>" >&2; exit 1; }
    do_add_comment "$1" "$2"
    ;;
  get-issue)
    [ $# -ge 1 ] || { echo "Usage: $0 get-issue <issue-key>" >&2; exit 1; }
    do_get_issue "$1"
    ;;
  link-issues)
    [ $# -ge 2 ] || { echo "Usage: $0 link-issues <from-key> <to-key> [link-type]" >&2; exit 1; }
    do_link_issues "$1" "$2" "${3:-Relates}"
    ;;
  attach-file)
    [ $# -ge 2 ] || { echo "Usage: $0 attach-file <issue-key> <file-path>" >&2; exit 1; }
    do_attach_file "$1" "$2"
    ;;
  log-work)
    [ $# -ge 2 ] || { echo "Usage: $0 log-work <issue-key> <time-spent> [comment]" >&2; exit 1; }
    do_log_work "$1" "$2" "${3:-}"
    ;;
  resolve-key)
    [ $# -ge 1 ] || { echo "Usage: $0 resolve-key <url-or-key>" >&2; exit 1; }
    do_resolve_key "$1"
    ;;
  *)
    echo "Usage: $0 {setup|test-auth|get-epic|create-epic|create-story|create-subtask|update-issue|transition|add-comment|get-issue|link-issues|attach-file|log-work|resolve-key}" >&2
    exit 1
    ;;
esac
