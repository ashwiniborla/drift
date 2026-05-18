#!/usr/bin/env bash
# confluence.sh — Confluence API helper for harness pipeline
#
# Usage:
#   confluence.sh create-page  <parent-page-id> <title> <markdown-file>
#   confluence.sh pull-page    <page-id>        <output-markdown-file>   — pull current page to local markdown
#   confluence.sh update-page  <page-id>        <markdown-file>          — pull-before-push: detects confluence-side edits, warns, then pushes
#   confluence.sh update-page-md <page-id> <markdown-file>   # alias — explicit markdown-aware update
#   confluence.sh publish-page <parent-page-id> <title> <markdown-file> [<existing-page-id>]
#       — idempotent: if <existing-page-id> is non-empty, runs update-page;
#         otherwise create-page. Use this for SCOPE_CHANGE re-runs and the
#         tweak/scope-change loops in designer/hld/lld/planner so the same
#         page is reused across rounds (no duplicates, no title-collision 400s).
#   confluence.sh get-comments <page-id>
#   confluence.sh check-lgtm <page-id>
#   confluence.sh get-page-id <page-url>
#   confluence.sh setup          — interactive setup: prompts for URL + token, saves to credential file
#   confluence.sh test-auth      — verify credentials work
#
# Auth resolution order (first match wins):
#   1. CONFLUENCE_TOKEN env var (for CI or pre-set sessions)
#   2. ~/.harness/secrets/atlassian.credentials (global, persisted by 'setup' — survives across repos and sessions)
#   3. .confluence-credentials file in repo root (gitignored, created by 'setup')
#
# Auth: Atlassian Cloud only — Basic auth with email + API token.
# Same token as Jira (both use Atlassian Cloud at flipkart.atlassian.net).
# Token: https://id.atlassian.com/manage-profile/security/api-tokens
#
# Base URL resolution order:
#   1. CONFLUENCE_BASE_URL env var
#   2. confluence-base-url field in harness-state.md
#   3. .confluence-credentials file
#
# Email resolution order (Cloud only):
#   1. CONFLUENCE_EMAIL env var
#   2. email: field in .confluence-credentials
#
# MARKDOWN CONVERSION (create-page / update-page / update-page-md):
#   Pages are converted from Markdown to Confluence storage format inline via
#   Python3 (no external deps). Supports headings, paragraphs, lists (bulleted
#   + numbered, run-wrapped in <ul>/<ol>), blockquotes, tables, inline code,
#   links, bold, code fences. Mermaid blocks become mmdc-rendered PNG attachments.
#
# SECURITY:
#   - .confluence-credentials is gitignored (setup command adds it)
#   - Tokens are NEVER stored in harness-state.md (which is committed)
#   - The credential file has 600 permissions (owner read/write only)

set -euo pipefail

# --- Paths -------------------------------------------------------------------
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
HARNESS_STATE="${REPO_ROOT}/harness-state.md"
CRED_FILE="${REPO_ROOT}/.confluence-credentials"
GLOBAL_CRED_FILE="${HOME}/.harness/secrets/atlassian.credentials"

# --- Load base URL -----------------------------------------------------------
# Default base URL for Flipkart Atlassian Cloud
DEFAULT_CONFLUENCE_URL="https://flipkart.atlassian.net/wiki"

CONFLUENCE_BASE_URL="${CONFLUENCE_BASE_URL:-}"

# --- Helpers: portable key-value extraction ---------------------------------
# `grep -oP` with \K is GNU-only and breaks on macOS BSD grep. `read_kv` reads
# the first line matching `^<key>:` in a file and returns the trimmed value
# (whatever follows the FIRST colon). Works for URL values too because we
# only split on the first `:`.
read_kv() {
  local file="$1"
  local key="$2"
  [ -f "$file" ] || { echo ""; return; }
  awk -v k="$key" '
    BEGIN { pat = "^[ \t]*" k ":" }
    $0 ~ pat {
      sub(pat "[ \t]*", "", $0)
      # Strip trailing whitespace / CR.
      sub(/[ \t\r]+$/, "", $0)
      print
      exit
    }
  ' "$file" 2>/dev/null || echo ""
}

if [ -z "$CONFLUENCE_BASE_URL" ] && [ -f "$HARNESS_STATE" ]; then
  CONFLUENCE_BASE_URL=$(read_kv "$HARNESS_STATE" "confluence-base-url")
fi

if [ -z "$CONFLUENCE_BASE_URL" ] && [ -f "$GLOBAL_CRED_FILE" ]; then
  CONFLUENCE_BASE_URL=$(read_kv "$GLOBAL_CRED_FILE" "confluence-base-url")
fi

if [ -z "$CONFLUENCE_BASE_URL" ] && [ -f "$CRED_FILE" ]; then
  CONFLUENCE_BASE_URL=$(read_kv "$CRED_FILE" "base-url")
fi

# Fall back to default if nothing found
CONFLUENCE_BASE_URL="${CONFLUENCE_BASE_URL:-$DEFAULT_CONFLUENCE_URL}"

# --- Detect Atlassian Cloud --------------------------------------------------
# Cloud hosts end in `.atlassian.net`; everything else is assumed to be
# Data Center / Server. This determines whether we pick Basic vs. Bearer auth
# Atlassian Cloud only — no Data Center / Server path needed.

# --- Load auth token ---------------------------------------------------------
resolve_token() {
  # 1. Environment variable
  if [ -n "${CONFLUENCE_TOKEN:-}" ]; then
    echo "$CONFLUENCE_TOKEN"
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

  # 3. Per-repo credential file (gitignored, 600 perms)
  if [ -f "$CRED_FILE" ]; then
    local file_token
    file_token=$(read_kv "$CRED_FILE" "token")
    if [ -n "$file_token" ]; then
      echo "$file_token"
      return 0
    fi
  fi

  # 4. No token found
  return 1
}

# --- Load Atlassian account email (Cloud only) -------------------------------
# Returns empty string when no email is configured — callers treat that as
# "use Bearer auth" (Data Center mode).
resolve_email() {
  if [ -n "${CONFLUENCE_EMAIL:-}" ]; then
    echo "$CONFLUENCE_EMAIL"
    return 0
  fi

  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local global_email
    global_email=$(read_kv "$GLOBAL_CRED_FILE" "email")
    if [ -n "$global_email" ]; then
      echo "$global_email"
      return 0
    fi
  fi

  if [ -f "$CRED_FILE" ]; then
    local file_email
    file_email=$(read_kv "$CRED_FILE" "email")
    if [ -n "$file_email" ]; then
      echo "$file_email"
      return 0
    fi
  fi

  echo ""
}

# --- Command: setup (interactive) --------------------------------------------
do_setup() {
  echo "━━━ Confluence Credential Setup ━━━"
  echo ""

  # Base URL — use the default silently. Override with CONFLUENCE_BASE_URL env
  # var if you need a non-default instance for this setup run.
  local current_url="${CONFLUENCE_BASE_URL:-$DEFAULT_CONFLUENCE_URL}"
  current_url="${current_url%/}"

  if [ -z "$current_url" ]; then
    echo "ERROR: Base URL is required (set CONFLUENCE_BASE_URL or update DEFAULT_CONFLUENCE_URL in confluence.sh)." >&2
    exit 1
  fi
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
        local test_response
        test_response=$(curl -sf --max-time 10 \
          -u "${global_email}:${global_token}" \
          "${current_url}/rest/api/user/current" 2>/dev/null || echo "")
        if [ -n "$test_response" ]; then
          local display_name
          display_name=$(echo "$test_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('displayName','unknown'))" 2>/dev/null)
          echo "Authenticated as: $display_name"
          {
            echo "# Confluence credentials (reused from global ~/.harness/secrets/atlassian.credentials)"
            echo "base-url: ${current_url}"
            echo "email: ${global_email}"
            echo "token: ${global_token}"
          } > "$CRED_FILE"
          chmod 600 "$CRED_FILE"
          echo "Saved to .confluence-credentials (gitignored, 600 perms)."
          exit 0
        else
          echo "Global token didn't work — entering manual setup."
        fi
      fi
    fi
  fi

  # Check if .jira-credentials exists — reuse the same Atlassian token
  local jira_cred="${REPO_ROOT}/.jira-credentials"
  if [ -f "$jira_cred" ]; then
    local jira_email jira_token
    jira_email=$(read_kv "$jira_cred" "email")
    jira_token=$(read_kv "$jira_cred" "token")
    if [ -n "$jira_email" ] && [ -n "$jira_token" ]; then
      echo ""
      echo "Found existing Jira credentials (same Atlassian account)."
      echo "  Email: $jira_email"
      read -rp "Reuse these for Confluence? [Y/n]: " reuse_choice
      if [[ ! "$reuse_choice" =~ ^[Nn] ]]; then
        local test_response
        test_response=$(curl -sf --max-time 10 \
          -u "${jira_email}:${jira_token}" \
          "${current_url}/rest/api/user/current" 2>/dev/null || echo "")
        if [ -n "$test_response" ]; then
          local display_name
          display_name=$(echo "$test_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('displayName','unknown'))" 2>/dev/null)
          echo "Authenticated as: $display_name (reusing Jira token)"
          {
            echo "# Confluence credentials (reused from Jira)"
            echo "base-url: ${current_url}"
            echo "email: ${jira_email}"
            echo "token: ${jira_token}"
          } > "$CRED_FILE"
          chmod 600 "$CRED_FILE"
          echo "Saved to .confluence-credentials (gitignored, 600 perms)."
          exit 0
        else
          echo "Jira token didn't work for Confluence — entering manual setup."
        fi
      fi
    fi
  fi

  # Atlassian Cloud: email + API token (same token for Confluence and Jira)
  echo ""
  echo "Atlassian Cloud uses Basic auth: email + API token."
  echo "Generate a token at: https://id.atlassian.com/manage-profile/security/api-tokens"
  echo "(Same token works for both Confluence and Jira)"
  echo ""

  local input_email=""
  read -rp "Atlassian email (e.g., you@flipkart.com): " input_email
  if [ -z "$input_email" ]; then
    echo "ERROR: Email is required." >&2
    exit 1
  fi

  echo ""
  read -rsp "API token (hidden): " input_token
  echo ""

  if [ -z "$input_token" ]; then
    echo "ERROR: Token is required." >&2
    exit 1
  fi

  # Test the credentials
  echo "Testing connection..."
  local test_response
  test_response=$(curl -sf --max-time 10 \
    -u "${input_email}:${input_token}" \
    "${current_url}/rest/api/user/current" 2>/dev/null || echo "")

  if [ -z "$test_response" ]; then
    echo "ERROR: Authentication failed. Check your URL, email, and token." >&2
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
    echo "# Generated by: confluence.sh setup — do NOT commit this file"
    echo "confluence-base-url: ${current_url}"
    echo "jira-base-url: ${current_url%/wiki}"
    echo "email: ${input_email}"
    echo "token: ${input_token}"
  } > "$GLOBAL_CRED_FILE"
  chmod 600 "$GLOBAL_CRED_FILE"
  echo "Saved to ~/.harness/secrets/atlassian.credentials (global, 600 perms)."

  # Save to per-repo credential file
  {
    echo "# Confluence credentials for harness pipeline"
    echo "# This file is gitignored — do NOT commit it"
    echo "# Generated by: confluence.sh setup"
    echo "base-url: ${current_url}"
    echo "email: ${input_email}"
    echo "token: ${input_token}"
  } > "$CRED_FILE"
  chmod 600 "$CRED_FILE"

  # Ensure .gitignore has the credential file
  if [ -f "${REPO_ROOT}/.gitignore" ]; then
    if ! grep -qF ".confluence-credentials" "${REPO_ROOT}/.gitignore"; then
      echo ".confluence-credentials" >> "${REPO_ROOT}/.gitignore"
      echo "Added .confluence-credentials to .gitignore"
    fi
  else
    echo ".confluence-credentials" > "${REPO_ROOT}/.gitignore"
    echo "Created .gitignore with .confluence-credentials"
  fi


  echo ""
  echo "Setup complete. Credentials saved to .confluence-credentials (gitignored, 600 perms)."
  echo "You can also set CONFLUENCE_TOKEN env var to override."
  exit 0
}

# --- Resolve auth for API calls ----------------------------------------------
# CURL_AUTH_ARGS is an array — all API callers invoke
# `curl "${CURL_AUTH_ARGS[@]}" ...`. Atlassian Cloud only: Basic auth with
# email + API token.
CONFLUENCE_EMAIL_RESOLVED=""
CURL_AUTH_ARGS=()

init_auth() {
  if ! CONFLUENCE_TOKEN=$(resolve_token); then
    echo "ERROR: No Confluence credentials found." >&2
    echo "Run: bash scripts/agent/confluence.sh setup" >&2
    echo "(Same Atlassian API token works for both Confluence and Jira)" >&2
    exit 1
  fi

  if [ -z "$CONFLUENCE_BASE_URL" ]; then
    echo "ERROR: Confluence base URL not configured." >&2
    echo "Run: bash scripts/agent/confluence.sh setup" >&2
    exit 1
  fi

  CONFLUENCE_EMAIL_RESOLVED=$(resolve_email)

  if [ -z "$CONFLUENCE_EMAIL_RESOLVED" ]; then
    echo "ERROR: No email configured for Atlassian Cloud auth." >&2
    echo "Run: bash scripts/agent/confluence.sh setup" >&2
    exit 1
  fi

  CURL_AUTH_ARGS=(-u "${CONFLUENCE_EMAIL_RESOLVED}:${CONFLUENCE_TOKEN}")
}

# --- Command: test-auth ------------------------------------------------------
do_test_auth() {
  local response
  response=$(curl -sf --max-time 10 \
    "${CURL_AUTH_ARGS[@]}" \
    "${CONFLUENCE_BASE_URL}/rest/api/user/current" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "AUTH_FAILED: Could not authenticate with Confluence at ${CONFLUENCE_BASE_URL}" >&2
    exit 1
  fi

  local display_name
  display_name=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('displayName','unknown'))" 2>/dev/null || echo "unknown")
  echo "AUTH_OK:${display_name}"
  exit 0
}

# --- Markdown → Confluence storage-format converter --------------------------
# Emits pure storage-format HTML on stdout given a markdown file path.
# Mermaid code blocks are rendered locally via mmdc (mermaid-cli) → PNG attachment.
# mmdc is a mandatory prerequisite — install via: npm install -g @mermaid-js/mermaid-cli
# Fixes over a naive line-by-line converter:
#   * contiguous "- item" runs wrapped in <ul>, "1. item" runs in <ol>
#   * blockquotes ("> …") wrapped in <blockquote>
#   * table separator rows (|---|---|) skipped
#   * inline HTML-escape for text outside backticks / links / bold
md_to_storage_html() {
  local md_file="$1"
  python3 - "$md_file" <<'PYEOF'
import html, re, sys

path = sys.argv[1]
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

_mermaid_counter = [0]
_mermaid_pngs = []   # list of (filename, filepath) — uploaded after page update

def mermaid_to_attachment(diagram_text: str) -> str:
    """Render mermaid to PNG via local mmdc at high resolution.
    Returns the attachment filename to reference in <ri:attachment>.
    mmdc is mandatory — errors if not installed.
    Uses high DPI PNG (scale=4) for crisp rendering on Confluence."""
    import shutil, subprocess, tempfile, os
    _mermaid_counter[0] += 1
    filename = f'mermaid_diagram_{_mermaid_counter[0]}.png'
    mmdc = shutil.which('mmdc')
    if not mmdc:
        sys.stderr.write('ERROR: mmdc (mermaid-cli) not found. Install it:\n')
        sys.stderr.write('  npm install -g @mermaid-js/mermaid-cli\n')
        sys.stderr.write('Mermaid diagram will be shown as source code only.\n')
        return ''
    tmpdir = tempfile.mkdtemp()
    in_path = os.path.join(tmpdir, 'diagram.mmd')
    out_path = os.path.join(tmpdir, filename)
    # Write a puppeteer config for higher quality rendering
    config_path = os.path.join(tmpdir, 'mmdc-config.json')
    with open(config_path, 'w') as f:
        f.write('{"theme":"default","themeVariables":{"fontSize":"16px"}}')
    with open(in_path, 'w') as f:
        f.write(diagram_text)
    # Render at high resolution: scale=4 produces 4x DPI PNG (crisp on retina + Confluence)
    # -w 2400 = base width, × scale=4 = effective 9600px for complex diagrams
    result = subprocess.run(
        [mmdc, '-i', in_path, '-o', out_path,
         '-b', 'white', '-w', '2400', '-s', '4',
         '-c', config_path, '--quiet'],
        capture_output=True, timeout=120
    )
    if result.returncode == 0 and os.path.exists(out_path):
        file_size = os.path.getsize(out_path)
        if file_size < 100:
            sys.stderr.write(f'WARN: mmdc produced a tiny file ({file_size}B) — diagram may be invalid\n')
            sys.stderr.write(f'Diagram source:\n{diagram_text[:200]}...\n')
            return ''
        _mermaid_pngs.append((filename, out_path))
        sys.stderr.write(f'  Rendered {filename}: {file_size//1024}KB\n')
        return filename
    stderr_msg = result.stderr.decode('utf-8', errors='replace')[:500]
    sys.stderr.write(f'ERROR: mmdc failed (exit {result.returncode}):\n{stderr_msg}\n')
    sys.stderr.write(f'Diagram source ({len(diagram_text)} chars):\n{diagram_text[:300]}...\n')
    return ''

_INLINE_CODE_RE = re.compile(r'`([^`]+)`')
_BOLD_RE = re.compile(r'\*\*(.+?)\*\*')
_LINK_RE = re.compile(r'\[([^\]]+)\]\(([^)]+)\)')

def inline_format(text: str) -> str:
    # Escape everything first, then reintroduce inline markdown as HTML.
    # We swap inline code out first with tokens so its contents aren't re-matched.
    tokens = []
    def _stash_code(m):
        tokens.append(html.escape(m.group(1)))
        return f"\x00CODE{len(tokens)-1}\x00"
    safe = _INLINE_CODE_RE.sub(_stash_code, text)

    link_tokens = []
    def _stash_link(m):
        link_tokens.append((html.escape(m.group(1)), html.escape(m.group(2), quote=True)))
        return f"\x00LINK{len(link_tokens)-1}\x00"
    safe = _LINK_RE.sub(_stash_link, safe)

    # Escape the rest
    safe = html.escape(safe, quote=False)

    # Bold (operates on escaped text; ** survives html.escape)
    safe = _BOLD_RE.sub(r'<strong>\1</strong>', safe)

    # Re-insert code + link tokens
    for i, code in enumerate(tokens):
        safe = safe.replace(f"\x00CODE{i}\x00", f"<code>{code}</code>")
    for i, (lbl, href) in enumerate(link_tokens):
        safe = safe.replace(f"\x00LINK{i}\x00", f'<a href="{href}">{lbl}</a>')
    return safe

def is_separator_row(cells):
    return bool(cells) and all(re.match(r'^:?-+:?$', c.strip()) for c in cells if c.strip())

def parse_table(lines):
    rows = [[c.strip() for c in l.strip().strip('|').split('|')] for l in lines]
    if len(rows) < 2:
        return '\n'.join(f'<p>{inline_format(l)}</p>' for l in lines)

    # First non-separator row is the header.
    data_rows = [r for r in rows if not is_separator_row(r)]
    if not data_rows:
        return ''

    n_cols = max(len(r) for r in data_rows)

    out = ['<table>']
    # Explicit colgroup helps Confluence compute widths; avoids "flat" layout.
    out.append('<colgroup>' + '<col/>' * n_cols + '</colgroup>')
    out.append('<tbody>')
    for i, cells in enumerate(data_rows):
        # Pad short rows so the table isn't skewed.
        while len(cells) < n_cols:
            cells.append('')
        tag = 'th' if i == 0 else 'td'
        out.append('<tr>')
        for c in cells:
            # Empty <p> cells are silently dropped by Confluence Cloud.
            content = inline_format(c) if c else '&#160;'
            out.append(f'<{tag}>{content}</{tag}>')
        out.append('</tr>')
    out.append('</tbody></table>')
    return '\n'.join(out)

lines = text.split('\n')
out = []
in_code = False
code_buf = []
lang = ''
table_buf = []
ul_open = False
ol_open = False
bq_open = False

def close_lists():
    global ul_open, ol_open
    if ul_open:
        out.append('</ul>')
        ul_open = False
    if ol_open:
        out.append('</ol>')
        ol_open = False

def close_bq():
    global bq_open
    if bq_open:
        out.append('</blockquote>')
        bq_open = False

def flush_table():
    if table_buf:
        out.append(parse_table(table_buf))
        table_buf.clear()

for raw in lines:
    line = raw  # keep leading whitespace for list/indent detection
    stripped = line.strip()

    # Code fence toggle
    if stripped.startswith('```'):
        flush_table(); close_lists(); close_bq()
        if not in_code:
            in_code = True
            lang = stripped[3:].strip()
            code_buf = []
        else:
            in_code = False
            code_text = '\n'.join(code_buf)
            if lang == 'mermaid':
                attach_name = mermaid_to_attachment(code_text)
                if attach_name:
                    # Reference the PNG attachment (uploaded after page update)
                    out.append(
                        f'<ac:image ac:align="center" ac:layout="center" ac:width="900">'
                        f'<ri:attachment ri:filename="{html.escape(attach_name, quote=True)}"/>'
                        f'</ac:image>'
                    )
                # Collapsible fallback: preserve mermaid source so readers can
                # re-render / edit.
                safe_src = code_text.replace(']]>', ']]]]><![CDATA[>')
                out.append(
                    '<ac:structured-macro ac:name="expand">'
                    '<ac:parameter ac:name="title">Diagram source (mermaid)</ac:parameter>'
                    '<ac:rich-text-body>'
                    '<ac:structured-macro ac:name="code">'
                    '<ac:parameter ac:name="language">text</ac:parameter>'
                    f'<ac:plain-text-body><![CDATA[{safe_src}]]></ac:plain-text-body>'
                    '</ac:structured-macro>'
                    '</ac:rich-text-body>'
                    '</ac:structured-macro>'
                )
            else:
                lang_attr = (
                    f'<ac:parameter ac:name="language">{html.escape(lang)}</ac:parameter>'
                    if lang else ''
                )
                # CDATA-safe encoding
                safe_code = code_text.replace(']]>', ']]]]><![CDATA[>')
                out.append(
                    f'<ac:structured-macro ac:name="code">{lang_attr}'
                    f'<ac:plain-text-body><![CDATA[{safe_code}]]></ac:plain-text-body>'
                    f'</ac:structured-macro>'
                )
            lang = ''
        continue

    if in_code:
        code_buf.append(raw)
        continue

    # Table line (pipe-delimited row)
    if stripped.startswith('|') and stripped.endswith('|') and len(stripped) > 1:
        close_lists(); close_bq()
        table_buf.append(line)
        continue
    elif table_buf:
        flush_table()

    # Blockquote
    if stripped.startswith('>'):
        close_lists()
        if not bq_open:
            out.append('<blockquote>')
            bq_open = True
        content = stripped[1:].lstrip()
        out.append(f'<p>{inline_format(content)}</p>')
        continue
    else:
        close_bq()

    # Unordered list item
    m_ul = re.match(r'^\s*[-*]\s+(.*)$', line)
    m_ol = re.match(r'^\s*\d+\.\s+(.*)$', line)
    if m_ul:
        if ol_open:
            out.append('</ol>'); ol_open = False
        if not ul_open:
            out.append('<ul>'); ul_open = True
        out.append(f'<li>{inline_format(m_ul.group(1))}</li>')
        continue
    if m_ol:
        if ul_open:
            out.append('</ul>'); ul_open = False
        if not ol_open:
            out.append('<ol>'); ol_open = True
        out.append(f'<li>{inline_format(m_ol.group(1))}</li>')
        continue
    # Not a list line → close any open list
    close_lists()

    # Headings
    if line.startswith('# '):
        out.append(f'<h1>{inline_format(line[2:])}</h1>')
    elif line.startswith('## '):
        out.append(f'<h2>{inline_format(line[3:])}</h2>')
    elif line.startswith('### '):
        out.append(f'<h3>{inline_format(line[4:])}</h3>')
    elif line.startswith('#### '):
        out.append(f'<h4>{inline_format(line[5:])}</h4>')
    elif stripped == '[TOC]':
        # Table of Contents macro
        out.append('<ac:structured-macro ac:name="toc"><ac:parameter ac:name="maxLevel">3</ac:parameter></ac:structured-macro>')
    elif (stripped.startswith('[EMBED:') or stripped.startswith('[CARD:')) and stripped.endswith(']'):
        # Smart Link embed — renders as an embedded rich preview (video player, slide deck, etc.)
        prefix_len = 7 if stripped.startswith('[EMBED:') else 6
        embed_url = stripped[prefix_len:-1]
        out.append(f'<a href="{html.escape(embed_url, quote=True)}" data-layout="center" data-width="100.00" data-card-appearance="embed">{html.escape(embed_url)}</a>')
    elif stripped in ('', '---'):
        # blank line or HR — emit a spacer paragraph
        out.append('<p> </p>')
    else:
        out.append(f'<p>{inline_format(line)}</p>')

flush_table(); close_lists(); close_bq()
sys.stdout.write('\n'.join(out))

# Output mermaid PNG paths on stderr so the caller can upload them as attachments.
# Format: MERMAID_PNG:<filename>:<filepath>
for fname, fpath in _mermaid_pngs:
    sys.stderr.write(f'MERMAID_PNG:{fname}:{fpath}\n')
PYEOF
}

# Back-compat shim: emits the Confluence REST `body.storage` JSON object.
# Used by legacy create_page / update_page callers below.
md_to_confluence_body() {
  local md_file="$1"
  local storage
  storage=$(md_to_storage_html "$md_file")
  STORAGE_HTML="$storage" python3 -c '
import json, os
print(json.dumps({"value": os.environ["STORAGE_HTML"], "representation": "storage"}))
'
}

# --- Command: create-page ----------------------------------------------------
create_page() {
  local parent_id="$1"
  local title="$2"
  local md_file="$3"

  if [ ! -f "$md_file" ]; then
    echo "ERROR: Markdown file not found: $md_file" >&2
    exit 1
  fi

  local space_key
  space_key=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${CONFLUENCE_BASE_URL}/rest/api/content/${parent_id}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['space']['key'])" 2>/dev/null)

  if [ -z "$space_key" ]; then
    echo "ERROR: Could not resolve space key from parent page ${parent_id}" >&2
    exit 1
  fi

  # Capture HTML on stdout and mermaid PNG paths on stderr
  local mermaid_log
  mermaid_log=$(mktemp)
  local storage
  storage=$(md_to_storage_html "$md_file" 2>"$mermaid_log")

  local payload
  payload=$(STORAGE_HTML="$storage" TITLE="$title" PARENT_ID="$parent_id" SPACE_KEY="$space_key" \
    python3 -c '
import json, os
page = {
    "type": "page",
    "title": os.environ["TITLE"],
    "ancestors": [{"id": os.environ["PARENT_ID"]}],
    "space": {"key": os.environ["SPACE_KEY"]},
    "body": {"storage": {"value": os.environ["STORAGE_HTML"], "representation": "storage"}},
}
print(json.dumps(page))
')

  local response
  response=$(curl -sf -X POST \
    "${CURL_AUTH_ARGS[@]}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${CONFLUENCE_BASE_URL}/rest/api/content")

  local page_id
  page_id=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

  if [ -z "$page_id" ]; then
    echo "ERROR: Failed to create Confluence page" >&2
    echo "$response" >&2
    rm -f "$mermaid_log"
    exit 1
  fi

  # Upload mermaid PNG attachments (rendered locally by mmdc during md_to_storage_html)
  # Uses POST to /data endpoint if attachment exists (update), POST to /attachment if new (create).
  if [ -f "$mermaid_log" ]; then
    while IFS=: read -r tag fname fpath; do
      [ "$tag" = "MERMAID_PNG" ] || continue
      [ -f "$fpath" ] || continue

      # Check if attachment with same filename already exists
      local existing_att_id
      existing_att_id=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
        "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}/child/attachment?filename=${fname}" 2>/dev/null | \
        python3 -c "import sys,json; r=json.load(sys.stdin).get('results',[]); print(r[0]['id'] if r else '')" 2>/dev/null || echo "")

      if [ -n "$existing_att_id" ]; then
        # Update existing attachment data
        curl -sf -X POST \
          "${CURL_AUTH_ARGS[@]}" \
          -H "X-Atlassian-Token: nocheck" \
          -F "file=@${fpath};filename=${fname}" \
          -F "minorEdit=true" \
          "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}/child/attachment/${existing_att_id}/data" > /dev/null 2>&1 \
          && echo "  UPDATED:${fname}" >&2 || echo "  UPDATE_FAILED:${fname}" >&2
      else
        # Create new attachment
        curl -sf -X POST \
          "${CURL_AUTH_ARGS[@]}" \
          -H "X-Atlassian-Token: nocheck" \
          -F "file=@${fpath};filename=${fname}" \
          -F "minorEdit=true" \
          "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}/child/attachment" > /dev/null 2>&1 \
          && echo "  ATTACHED:${fname}" >&2 || echo "  ATTACH_FAILED:${fname}" >&2
      fi

      rm -f "$fpath"
    done < "$mermaid_log"
    rm -f "$mermaid_log"
  fi

  local page_url="${CONFLUENCE_BASE_URL}/pages/viewpage.action?pageId=${page_id}"
  echo "PAGE_CREATED:${page_id}:${page_url}"
}

# --- Command: pull-page -------------------------------------------------------
# Fetches the current Confluence page body (storage format HTML) and converts
# it to markdown. Used for pull-before-push merge detection.
pull_page_md() {
  local page_id="$1"
  local out_file="$2"   # Where to write the pulled markdown

  local response
  response=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}?expand=body.storage,version")

  if [ -z "$response" ]; then
    echo "ERROR: Could not fetch page ${page_id}" >&2
    return 1
  fi

  # Extract storage HTML and convert to plaintext (section headers preserved)
  echo "$response" | python3 -c "
import sys, json, re, html as htmlmod

data = json.load(sys.stdin)
body = data['body']['storage']['value']
version = data['version']['number']

# Convert storage HTML to rough markdown for diffing
# TOC macro → [TOC]
text = re.sub(r'<ac:structured-macro[^>]*ac:name=\"toc\"[^>]*>.*?</ac:structured-macro>', '[TOC]', body, flags=re.DOTALL)
text = re.sub(r'<ac:structured-macro[^>]*ac:name=\"toc\"[^/]*/>', '[TOC]', text)
# Embed cards → [EMBED:url]
text = re.sub(r'<a [^>]*href=\"([^\"]+)\"[^>]*data-card-appearance=\"embed\"[^>]*>[^<]*</a>', r'[EMBED:\1]', text)
# Headings
text = re.sub(r'<h(\d)[^>]*>(.*?)</h\1>', lambda m: '#' * int(m.group(1)) + ' ' + m.group(2), text, flags=re.DOTALL)
# Strip remaining tags
text = re.sub(r'<[^>]+>', '\n', text)
# Decode entities
text = htmlmod.unescape(text)
# Collapse blank lines
lines = [l.strip() for l in text.split('\n')]
text = '\n'.join(l for l in lines if l)

with open('$out_file', 'w') as f:
    f.write(text)
print(f'PULLED:v{version}:{len(text)} chars')
" 2>/dev/null
}

# --- Command: update-page ----------------------------------------------------
# Pull-before-push: fetches the current Confluence page, detects if there are
# Confluence-side edits not in the local file, warns if so, then pushes.
# Intentionally does NOT send `ancestors` — Confluence preserves parent on
# update, and re-sending it risks accidental re-parenting when the caller
# doesn't know the current ancestor.
update_page() {
  local page_id="$1"
  local md_file="$2"

  if [ ! -f "$md_file" ]; then
    echo "ERROR: Markdown file not found: $md_file" >&2
    exit 1
  fi

  # --- Pull-before-push: detect Confluence-side edits -----------------------
  # Compare section headings (## markers) between Confluence and local file.
  # If Confluence has sections not in the local file, those were added via the
  # Confluence UI and would be lost on push. Warn the user.
  local pulled_md
  pulled_md=$(mktemp)
  pull_page_md "$page_id" "$pulled_md" 2>/dev/null || true

  if [ -s "$pulled_md" ]; then
    local merge_result
    merge_result=$(python3 -c "
import re, sys

# Extract section headings from pulled Confluence content
with open('$pulled_md') as f:
    conf_text = f.read()
conf_headings = set(l.strip() for l in conf_text.split('\n') if l.strip().startswith('#'))

# Extract section headings from local markdown
with open('$md_file') as f:
    local_text = f.read()
local_headings = set(l.strip() for l in local_text.split('\n') if l.strip().startswith('#'))

# Sections in Confluence but not in local file = Confluence-side additions
conf_only = conf_headings - local_headings
if conf_only:
    print('CONFLUENCE_SECTIONS_ADDED')
    for h in sorted(conf_only):
        print(f'  {h}')
else:
    print('OK')
" 2>/dev/null || echo "OK")

    if echo "$merge_result" | grep -q "CONFLUENCE_SECTIONS_ADDED"; then
      echo "  WARN: Confluence page has sections not in local file:" >&2
      echo "$merge_result" | grep "^  " >&2
      echo "  These sections may have been added via the Confluence UI." >&2
      echo "  Consider pulling first: confluence.sh pull-page $page_id <output.md>" >&2
      echo "  Proceeding with push (Confluence-only sections will be overwritten)." >&2
    fi

    rm -f "$pulled_md"
  else
    rm -f "$pulled_md"
  fi

  # --- Fetch current version + title ----------------------------------------
  local meta
  meta=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}")

  if [ -z "$meta" ]; then
    echo "ERROR: Could not fetch page ${page_id} (check auth + page-id)" >&2
    exit 1
  fi

  local current_version title
  current_version=$(echo "$meta" | python3 -c "import sys,json; print(json.load(sys.stdin)['version']['number'])" 2>/dev/null)
  title=$(echo "$meta" | python3 -c "import sys,json; print(json.load(sys.stdin)['title'])" 2>/dev/null)

  if [ -z "$current_version" ] || [ -z "$title" ]; then
    echo "ERROR: Could not parse version/title for page ${page_id}" >&2
    exit 1
  fi

  local new_version=$((current_version + 1))

  # Capture HTML on stdout and mermaid PNG paths on stderr
  local mermaid_log
  mermaid_log=$(mktemp)
  local storage
  storage=$(md_to_storage_html "$md_file" 2>"$mermaid_log")

  local payload
  payload=$(STORAGE_HTML="$storage" TITLE="$title" NEW_VERSION="$new_version" \
    python3 -c '
import json, os
page = {
    "version": {"number": int(os.environ["NEW_VERSION"])},
    "title": os.environ["TITLE"],
    "type": "page",
    "body": {"storage": {"value": os.environ["STORAGE_HTML"], "representation": "storage"}},
}
print(json.dumps(page))
')

  local response
  response=$(curl -sf -X PUT \
    "${CURL_AUTH_ARGS[@]}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}" || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: PUT failed for page ${page_id}" >&2
    exit 1
  fi

  local updated_version
  updated_version=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['version']['number'])" 2>/dev/null || echo "?")

  # Upload mermaid PNG attachments (rendered locally by mmdc during md_to_storage_html)
  # Uses POST to /data endpoint if attachment exists (update), POST to /attachment if new (create).
  if [ -f "$mermaid_log" ]; then
    while IFS=: read -r tag fname fpath; do
      [ "$tag" = "MERMAID_PNG" ] || continue
      [ -f "$fpath" ] || continue

      # Check if attachment with same filename already exists
      local existing_att_id
      existing_att_id=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
        "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}/child/attachment?filename=${fname}" 2>/dev/null | \
        python3 -c "import sys,json; r=json.load(sys.stdin).get('results',[]); print(r[0]['id'] if r else '')" 2>/dev/null || echo "")

      if [ -n "$existing_att_id" ]; then
        # Update existing attachment data
        curl -sf -X POST \
          "${CURL_AUTH_ARGS[@]}" \
          -H "X-Atlassian-Token: nocheck" \
          -F "file=@${fpath};filename=${fname}" \
          -F "minorEdit=true" \
          "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}/child/attachment/${existing_att_id}/data" > /dev/null 2>&1 \
          && echo "  UPDATED:${fname}" >&2 || echo "  UPDATE_FAILED:${fname}" >&2
      else
        # Create new attachment
        curl -sf -X POST \
          "${CURL_AUTH_ARGS[@]}" \
          -H "X-Atlassian-Token: nocheck" \
          -F "file=@${fpath};filename=${fname}" \
          -F "minorEdit=true" \
          "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}/child/attachment" > /dev/null 2>&1 \
          && echo "  ATTACHED:${fname}" >&2 || echo "  ATTACH_FAILED:${fname}" >&2
      fi

      rm -f "$fpath"
    done < "$mermaid_log"
    rm -f "$mermaid_log"
  fi

  echo "PAGE_UPDATED:${page_id}:v${updated_version}"
}

# --- Command: publish-page ---------------------------------------------------
# Idempotent ensure-or-update for design / plan pages.
# Designed to be called by designer/hld/lld/planner agents for every publish
# (initial AND subsequent rounds — tweaks, scope-change re-applies, full
# SCOPE_CHANGE re-runs after upstream regeneration). The agent passes the
# value of `confluence-<role>-page` from harness-state.md as the optional 4th
# arg (empty if unset); the helper picks update-page or create-page accordingly.
#
# This is the single command that prevents the historical bug where SCOPE_CHANGE
# re-runs would unconditionally `create-page` and either fail with a duplicate
# title or orphan the previously-approved page.
publish_page() {
  local parent_id="$1"
  local title="$2"
  local md_file="$3"
  local existing_id="${4:-}"

  if [ -z "$parent_id" ] || [ -z "$title" ] || [ -z "$md_file" ]; then
    echo "ERROR: usage: publish-page <parent-id> <title> <md-file> [<existing-page-id>]" >&2
    exit 1
  fi

  if [ ! -f "$md_file" ]; then
    echo "ERROR: Markdown file not found: $md_file" >&2
    exit 1
  fi

  # Update path: an existing page id was supplied. Verify the page is still
  # reachable before PUTting; if the API returns 404 (e.g., page was deleted
  # in Confluence between rounds), fall back to create.
  if [ -n "$existing_id" ]; then
    local probe
    probe=$(curl -sf -o /dev/null -w "%{http_code}" "${CURL_AUTH_ARGS[@]}" \
      "${CONFLUENCE_BASE_URL}/rest/api/content/${existing_id}" 2>/dev/null || echo "000")

    if [ "$probe" = "200" ]; then
      # Reuse update_page for the actual PUT so version bumping + storage
      # conversion stay in one place.
      local out
      out=$(update_page "$existing_id" "$md_file") || exit $?
      # Normalize output to PAGE_PUBLISHED for callers; preserve version.
      local ver
      ver=$(echo "$out" | sed -nE 's/^PAGE_UPDATED:[0-9]+:v(.*)$/\1/p')
      echo "PAGE_PUBLISHED:${existing_id}:UPDATED:v${ver:-?}"
      return 0
    fi

    echo "WARN: existing-page-id ${existing_id} not reachable (HTTP ${probe}); falling back to create-page." >&2
  fi

  # Create path: no existing id (or stale id 404'd). Reuse create_page.
  local out
  out=$(create_page "$parent_id" "$title" "$md_file") || exit $?
  # create_page emits PAGE_CREATED:<id>:<url>; map to PAGE_PUBLISHED:<id>:CREATED:<url>
  local id url
  id=$(echo "$out" | sed -nE 's/^PAGE_CREATED:([0-9]+):.*$/\1/p')
  url=$(echo "$out" | sed -nE 's/^PAGE_CREATED:[0-9]+:(.*)$/\1/p')
  echo "PAGE_PUBLISHED:${id}:CREATED:${url}"
}

# --- Command: get-comments ---------------------------------------------------
get_comments() {
  local page_id="$1"

  local response
  response=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
    "${CONFLUENCE_BASE_URL}/rest/api/content/${page_id}/child/comment?expand=body.storage,version,extensions.inlineProperties&limit=50")

  # Extract comments with author and body text
  echo "$response" | python3 - <<'PYEOF'
import sys, json, re

data = json.load(sys.stdin)
results = data.get('results', [])
if not results:
    print('NO_COMMENTS')
    sys.exit(0)

for c in results:
    author = c.get('version', {}).get('by', {}).get('displayName', 'unknown')
    body_html = c.get('body', {}).get('storage', {}).get('value', '')
    body_text = re.sub(r'<[^>]+>', ' ', body_html)
    body_text = re.sub(r'\s+', ' ', body_text).strip()
    created = c.get('version', {}).get('when', '')
    comment_id = c.get('id', '')
    resolved = c.get('extensions', {}).get('resolution', {}).get('status', 'open')
    print(f'COMMENT:{comment_id}:{author}:{created}:{resolved}:{body_text}')
PYEOF
}

# --- Command: check-lgtm ----------------------------------------------------
check_lgtm() {
  local page_id="$1"

  local comments_output
  comments_output=$(get_comments "$page_id")

  if echo "$comments_output" | grep -qi "LGTM\|approved\|looks good\|ship it"; then
    # Find the LGTM comment
    local lgtm_line
    lgtm_line=$(echo "$comments_output" | grep -i "LGTM\|approved\|looks good\|ship it" | tail -1)
    echo "LGTM_FOUND:${lgtm_line}"
    exit 0
  else
    echo "NO_LGTM"
    # Print all comments so the agent can read feedback
    echo "$comments_output"
    exit 1
  fi
}

# --- Command: get-page-id from URL ------------------------------------------
get_page_id() {
  local page_url="$1"

  # Handle different Confluence URL formats:
  # https://confluence.example.com/pages/viewpage.action?pageId=12345
  # https://confluence.example.com/display/SPACE/Page+Title
  # https://confluence.example.com/wiki/spaces/SPACE/pages/12345/Page+Title

  local page_id=""

  # Use sed -E (POSIX / BSD / GNU) instead of `grep -oP \K` (GNU-only).

  # Format 1: pageId in query string
  if echo "$page_url" | grep -q "pageId="; then
    page_id=$(echo "$page_url" | sed -nE 's/.*[?&]pageId=([0-9]+).*/\1/p')
  fi

  # Format 2: /pages/12345/ in path (covers both DC `/pages/12345/...` and
  # Cloud `/wiki/spaces/KEY/pages/12345/...`)
  if [ -z "$page_id" ]; then
    page_id=$(echo "$page_url" | sed -nE 's|.*/pages/([0-9]+).*|\1|p')
  fi

  # Format 3: /display/SPACE/Title — need to resolve via API
  if [ -z "$page_id" ] && echo "$page_url" | grep -q "/display/"; then
    local space_and_title
    space_and_title=$(echo "$page_url" | sed -nE 's|.*/display/(.*)|\1|p')
    local space_key
    space_key=$(echo "$space_and_title" | cut -d'/' -f1)
    local title
    title=$(echo "$space_and_title" | cut -d'/' -f2- | sed 's/+/ /g' | python3 -c "import sys,urllib.parse; print(urllib.parse.unquote(sys.stdin.read().strip()))")

    page_id=$(curl -sf "${CURL_AUTH_ARGS[@]}" \
      "${CONFLUENCE_BASE_URL}/rest/api/content?spaceKey=${space_key}&title=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$title'))")&limit=1" \
      | python3 -c "import sys,json; r=json.load(sys.stdin)['results']; print(r[0]['id'] if r else '')" 2>/dev/null)
  fi

  if [ -z "$page_id" ]; then
    echo "ERROR: Could not extract page ID from URL: $page_url" >&2
    exit 1
  fi

  echo "PAGE_ID:${page_id}"
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
  pull-page)
    init_auth
    [ $# -ge 2 ] || { echo "Usage: $0 pull-page <page-id> <output-md-file>" >&2; exit 1; }
    pull_page_md "$1" "$2"
    ;;
  create-page)
    init_auth
    [ $# -ge 3 ] || { echo "Usage: $0 create-page <parent-id> <title> <md-file>" >&2; exit 1; }
    create_page "$1" "$2" "$3"
    ;;
  update-page|update-page-md)
    init_auth
    [ $# -ge 2 ] || { echo "Usage: $0 $COMMAND <page-id> <md-file>" >&2; exit 1; }
    update_page "$1" "$2"
    ;;
  publish-page)
    init_auth
    [ $# -ge 3 ] || { echo "Usage: $0 publish-page <parent-id> <title> <md-file> [<existing-page-id>]" >&2; exit 1; }
    publish_page "$1" "$2" "$3" "${4:-}"
    ;;
  get-comments)
    init_auth
    [ $# -ge 1 ] || { echo "Usage: $0 get-comments <page-id>" >&2; exit 1; }
    get_comments "$1"
    ;;
  check-lgtm)
    init_auth
    [ $# -ge 1 ] || { echo "Usage: $0 check-lgtm <page-id>" >&2; exit 1; }
    check_lgtm "$1"
    ;;
  get-page-id)
    init_auth
    [ $# -ge 1 ] || { echo "Usage: $0 get-page-id <page-url>" >&2; exit 1; }
    get_page_id "$1"
    ;;
  *)
    echo "Usage: $0 {setup|test-auth|pull-page|create-page|update-page|update-page-md|publish-page|get-comments|check-lgtm|get-page-id}" >&2
    exit 1
    ;;
esac
