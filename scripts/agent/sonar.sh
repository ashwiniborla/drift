#!/usr/bin/env bash
# sonar.sh — SonarQube REST API helper for harness pipeline
#
# Usage:
#   sonar.sh setup                                    — interactive credential setup
#   sonar.sh test-auth                                — verify credentials work
#   sonar.sh trigger-analysis <project-key>           — trigger sonar analysis (runs sonar-scanner or mvn sonar:sonar)
#   sonar.sh wait-for-analysis <project-key>          — poll until analysis completes (max 5 min)
#   sonar.sh check-quality-gate <project-key>         — check quality gate status (PASS/FAIL)
#   sonar.sh get-metrics <project-key>                — get coverage, bugs, vulns, code smells, security hotspots
#   sonar.sh get-issues <project-key> <severity>      — get issues by severity (BLOCKER, CRITICAL, MAJOR)
#   sonar.sh resolve-project <url-or-key>             — extract project key from URL or pass through
#
# Auth resolution order (first match wins):
#   1. SONAR_TOKEN env var
#   2. ~/.harness/secrets/sonar.credentials (global, token-only — shared across all repos + both instances)
#   3. .sonar-credentials file in repo root (legacy fallback only — setup no longer creates this)
#
# Sonar instances (Flipkart) — only these two are accepted, no custom URLs:
#   - Default: https://service.sonar-prod.fkcloud.in
#   - LTA:     http://service-lta.sonar-prod.fkcloud.in
#
# Token works across both instances. setup tests against both and saves token once globally.
#
# SECURITY:
#   - Token stored only in ~/.harness/secrets/sonar.credentials (0600, never committed)
#   - Tokens are NEVER stored in harness-state.md (which is committed)
#   - Per-repo .sonar-credentials is legacy; if present it is gitignored and readable as fallback

set -euo pipefail

# --- Paths -------------------------------------------------------------------
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
HARNESS_STATE="${REPO_ROOT}/harness-state.md"
CRED_FILE="${REPO_ROOT}/.sonar-credentials"
GLOBAL_CRED_FILE="${HOME}/.harness/secrets/sonar.credentials"

# --- Known Sonar instances (Flipkart) ----------------------------------------
SONAR_URL_DEFAULT="https://service.sonar-prod.fkcloud.in"
SONAR_URL_LTA="http://service-lta.sonar-prod.fkcloud.in"

# validate_sonar_url <url> — returns the url if it matches a known instance, else empty string
validate_sonar_url() {
  local url="${1%/}"  # strip trailing slash
  case "$url" in
    "$SONAR_URL_DEFAULT"|"$SONAR_URL_LTA") echo "$url" ;;
    *) echo "" ;;
  esac
}

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

# --- Load base URL -----------------------------------------------------------
# Resolution order: env var → harness-state.md → legacy .sonar-credentials → default
# Only the two known Flipkart Sonar instances are accepted; anything else falls back to default.
# NOTE: ~/.harness/secrets/sonar.credentials stores token only — no base-url.

SONAR_BASE_URL="${SONAR_BASE_URL:-}"
[ -n "$SONAR_BASE_URL" ] && SONAR_BASE_URL=$(validate_sonar_url "$SONAR_BASE_URL")

if [ -z "$SONAR_BASE_URL" ] && [ -f "$HARNESS_STATE" ]; then
  SONAR_BASE_URL=$(validate_sonar_url "$(read_kv "$HARNESS_STATE" "sonar-base-url")")
fi

# Legacy fallback: per-repo .sonar-credentials may carry base-url from old setup runs
if [ -z "$SONAR_BASE_URL" ] && [ -f "$CRED_FILE" ]; then
  SONAR_BASE_URL=$(validate_sonar_url "$(read_kv "$CRED_FILE" "base-url")")
fi

SONAR_BASE_URL="${SONAR_BASE_URL:-$SONAR_URL_DEFAULT}"

# --- Load auth token ---------------------------------------------------------
resolve_token() {
  # 1. Environment variable
  if [ -n "${SONAR_TOKEN:-}" ]; then
    echo "$SONAR_TOKEN"
    return 0
  fi

  # 2. Global credential file (~/.harness/secrets/sonar.credentials)
  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local global_token
    global_token=$(read_kv "$GLOBAL_CRED_FILE" "token")
    if [ -n "$global_token" ]; then
      echo "$global_token"
      return 0
    fi
  fi

  # 3. Per-repo credential file (.sonar-credentials)
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

# --- Build auth header -------------------------------------------------------
# SonarQube uses token-as-username with empty password (Basic auth)
build_auth_header() {
  local token
  if ! token=$(resolve_token); then
    echo "ERROR: No SonarQube credentials found." >&2
    echo "Run: bash scripts/agent/sonar.sh setup" >&2
    exit 1
  fi

  # SonarQube: token is the username, password is empty
  echo "Authorization: Basic $(echo -n "${token}:" | base64)"
}

# --- Command: setup ----------------------------------------------------------
do_setup() {
  echo "━━━ SonarQube Credential Setup ━━━"
  echo ""

  # Check global credential file first — reuse if present
  if [ -f "$GLOBAL_CRED_FILE" ]; then
    local existing_token
    existing_token=$(read_kv "$GLOBAL_CRED_FILE" "token")
    if [ -n "$existing_token" ]; then
      echo "Found existing global token in $GLOBAL_CRED_FILE"
      read -rp "Reuse this token? [Y/n]: " REUSE
      if [[ "${REUSE:-Y}" =~ ^[Yy]$ ]]; then
        echo ""
        _test_both_instances "$existing_token"
        return 0
      fi
    fi
  fi

  echo "Flipkart SonarQube instances:"
  echo "  • ${SONAR_URL_DEFAULT}"
  echo "  • ${SONAR_URL_LTA}"
  echo ""
  echo "Generate a token at either instance's /account/security page."
  echo "The same token works across both instances."
  echo ""
  read -rsp "SonarQube token: " sonar_token
  echo ""

  if [ -z "$sonar_token" ]; then
    echo "ERROR: Token cannot be empty." >&2
    exit 1
  fi

  # Test against both instances
  if ! _test_both_instances "$sonar_token"; then
    echo "ERROR: Token did not authenticate against any Sonar instance. Credentials not saved." >&2
    exit 1
  fi

  # Write global credential file — token only, no URL (URL resolved at runtime from sonar.properties / harness-state.md)
  mkdir -p "$(dirname "$GLOBAL_CRED_FILE")"
  chmod 700 "$(dirname "$GLOBAL_CRED_FILE")"
  cat > "$GLOBAL_CRED_FILE" <<CREDEOF
token: ${sonar_token}
CREDEOF
  chmod 600 "$GLOBAL_CRED_FILE"
  echo "Token saved globally to $GLOBAL_CRED_FILE (0600)."
  echo "Shared across all repos and both Sonar instances — setup only needed once per machine."
}

# Test a token against both known Sonar instances and report results.
# Returns 0 if at least one instance accepts the token.
_test_both_instances() {
  local token="$1"
  local auth_header="Authorization: Basic $(echo -n "${token}:" | base64)"
  local any_ok=false

  for instance_url in "$SONAR_URL_DEFAULT" "$SONAR_URL_LTA"; do
    local response valid
    response=$(curl -sf --connect-timeout 5 -H "$auth_header" \
      "${instance_url}/api/authentication/validate" 2>/dev/null || echo "")
    valid=$(echo "$response" | python3 -c \
      "import sys,json; print(json.load(sys.stdin).get('valid', False))" 2>/dev/null || echo "false")
    if [ "$valid" = "True" ] || [ "$valid" = "true" ]; then
      echo "  ✓ ${instance_url}"
      any_ok=true
    else
      echo "  ✗ ${instance_url} (unreachable or token rejected)"
    fi
  done

  $any_ok
}

# --- Command: test-auth ------------------------------------------------------
do_test_auth() {
  local auth_header
  auth_header=$(build_auth_header)

  local response
  response=$(curl -sf -H "$auth_header" \
    "${SONAR_BASE_URL}/api/authentication/validate" 2>/dev/null || echo "")

  local valid
  valid=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('valid', False))" 2>/dev/null || echo "false")

  if [ "$valid" = "True" ] || [ "$valid" = "true" ]; then
    echo "AUTH_OK (sonar=${SONAR_BASE_URL})"
    return 0
  else
    echo "ERROR: SonarQube auth failed at ${SONAR_BASE_URL}" >&2
    echo "Response: $response" >&2
    return 1
  fi
}

# --- Command: trigger-analysis -----------------------------------------------
do_trigger_analysis() {
  local project_key="$1"

  # Detect build tool and run analysis
  if [ -f "${REPO_ROOT}/pom.xml" ]; then
    echo "Detected Maven project — running mvn sonar:sonar" >&2
    local token
    token=$(resolve_token)
    (cd "$REPO_ROOT" && mvn sonar:sonar \
      -Dsonar.projectKey="$project_key" \
      -Dsonar.host.url="$SONAR_BASE_URL" \
      -Dsonar.token="$token" \
      -Dsonar.qualitygate.wait=false \
      2>&1 | tail -5) >&2
  elif [ -f "${REPO_ROOT}/build.gradle" ] || [ -f "${REPO_ROOT}/build.gradle.kts" ]; then
    echo "Detected Gradle project — running gradle sonarqube" >&2
    local token
    token=$(resolve_token)
    (cd "$REPO_ROOT" && ./gradlew sonarqube \
      -Dsonar.projectKey="$project_key" \
      -Dsonar.host.url="$SONAR_BASE_URL" \
      -Dsonar.token="$token" \
      2>&1 | tail -5) >&2
  else
    echo "ERROR: No recognized build tool (pom.xml / build.gradle). Run sonar-scanner manually." >&2
    exit 1
  fi

  echo "ANALYSIS_TRIGGERED:${project_key}"
}

# --- Command: wait-for-analysis ----------------------------------------------
do_wait_for_analysis() {
  local project_key="$1"
  local auth_header
  auth_header=$(build_auth_header)

  local max_attempts=30  # 30 × 10s = 5 min
  local attempt=0

  while [ $attempt -lt $max_attempts ]; do
    local response
    response=$(curl -sf -H "$auth_header" \
      "${SONAR_BASE_URL}/api/ce/component?component=${project_key}" 2>/dev/null || echo "")

    local status
    status=$(echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
current = data.get('current', {})
queue = data.get('queue', [])
if queue:
    print('PENDING')
elif current.get('status') == 'SUCCESS':
    print('SUCCESS')
elif current.get('status') == 'FAILED':
    print('FAILED')
elif current.get('status') == 'IN_PROGRESS':
    print('IN_PROGRESS')
else:
    print('UNKNOWN')
" 2>/dev/null || echo "UNKNOWN")

    case "$status" in
      SUCCESS)
        echo "ANALYSIS_COMPLETE:${project_key}"
        return 0
        ;;
      FAILED)
        echo "ANALYSIS_FAILED:${project_key}" >&2
        return 1
        ;;
      PENDING|IN_PROGRESS)
        attempt=$((attempt + 1))
        sleep 10
        ;;
      *)
        # No analysis found — may not have been triggered yet
        attempt=$((attempt + 1))
        sleep 10
        ;;
    esac
  done

  echo "ANALYSIS_TIMEOUT:${project_key} (waited 5 min)" >&2
  return 1
}

# --- Command: check-quality-gate ---------------------------------------------
do_check_quality_gate() {
  local project_key="$1"
  local auth_header
  auth_header=$(build_auth_header)

  local response
  response=$(curl -sf -H "$auth_header" \
    "${SONAR_BASE_URL}/api/qualitygates/project_status?projectKey=${project_key}" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Could not fetch quality gate for ${project_key}" >&2
    exit 1
  fi

  python3 -c "
import sys, json
data = json.load(sys.stdin)
status = data.get('projectStatus', {}).get('status', 'UNKNOWN')
conditions = data.get('projectStatus', {}).get('conditions', [])

print(f'QUALITY_GATE:{status}')
for c in conditions:
    metric = c.get('metricKey', '?')
    actual = c.get('actualValue', '?')
    threshold = c.get('errorThreshold', '?')
    cond_status = c.get('status', '?')
    print(f'  {metric}: {actual} (threshold: {threshold}) — {cond_status}')
" <<< "$response" 2>/dev/null
}

# --- Command: get-metrics ----------------------------------------------------
do_get_metrics() {
  local project_key="$1"
  local auth_header
  auth_header=$(build_auth_header)

  local metrics="coverage,new_coverage,bugs,vulnerabilities,security_hotspots,code_smells,reliability_rating,security_rating,sqale_rating,ncloc,duplicated_lines_density"

  local response
  response=$(curl -sf -H "$auth_header" \
    "${SONAR_BASE_URL}/api/measures/component?component=${project_key}&metricKeys=${metrics}" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Could not fetch metrics for ${project_key}" >&2
    exit 1
  fi

  python3 -c "
import sys, json
data = json.load(sys.stdin)
measures = data.get('component', {}).get('measures', [])

# Rating map: 1=A, 2=B, 3=C, 4=D, 5=E
rating_map = {'1.0': 'A', '2.0': 'B', '3.0': 'C', '4.0': 'D', '5.0': 'E'}

for m in measures:
    metric = m.get('metric', '?')
    value = m.get('value', '?')
    # Convert ratings to letter grades
    if metric.endswith('_rating'):
        value = rating_map.get(value, value)
    print(f'METRIC:{metric}:{value}')
" <<< "$response" 2>/dev/null
}

# --- Command: get-issues -----------------------------------------------------
do_get_issues() {
  local project_key="$1"
  local severity="$2"   # BLOCKER, CRITICAL, MAJOR, MINOR, INFO

  local auth_header
  auth_header=$(build_auth_header)

  local response
  response=$(curl -sf -H "$auth_header" \
    "${SONAR_BASE_URL}/api/issues/search?componentKeys=${project_key}&severities=${severity}&statuses=OPEN,CONFIRMED,REOPENED&ps=100" 2>/dev/null || echo "")

  if [ -z "$response" ]; then
    echo "ERROR: Could not fetch issues for ${project_key}" >&2
    exit 1
  fi

  python3 -c "
import sys, json
data = json.load(sys.stdin)
total = data.get('total', 0)
issues = data.get('issues', [])

print(f'ISSUES:{total}:{\"$severity\"}')
for i in issues[:20]:  # Show first 20
    rule = i.get('rule', '?')
    msg = i.get('message', '?')[:100]
    component = i.get('component', '?').split(':')[-1]
    line = i.get('line', '?')
    issue_type = i.get('type', '?')
    print(f'  {issue_type}:{component}:{line}:{rule}:{msg}')
" <<< "$response" 2>/dev/null
}

# --- Command: resolve-project ------------------------------------------------
do_resolve_project() {
  local input="$1"

  # If it's already a project key (no slashes, no http), pass through
  if echo "$input" | grep -qvE '(http|/)'; then
    echo "PROJECT:${input}"
    return 0
  fi

  # Extract from URL: .../dashboard?id=project-key or .../project/overview?id=project-key
  local key
  key=$(echo "$input" | grep -oE '[?&]id=([^&]+)' | head -1 | sed 's/.*id=//')

  if [ -n "$key" ]; then
    echo "PROJECT:${key}"
    return 0
  fi

  echo "ERROR: Could not extract project key from: $input" >&2
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
  trigger-analysis)
    [ $# -ge 1 ] || { echo "Usage: $0 trigger-analysis <project-key>" >&2; exit 1; }
    do_trigger_analysis "$1"
    ;;
  wait-for-analysis)
    [ $# -ge 1 ] || { echo "Usage: $0 wait-for-analysis <project-key>" >&2; exit 1; }
    do_wait_for_analysis "$1"
    ;;
  check-quality-gate)
    [ $# -ge 1 ] || { echo "Usage: $0 check-quality-gate <project-key>" >&2; exit 1; }
    do_check_quality_gate "$1"
    ;;
  get-metrics)
    [ $# -ge 1 ] || { echo "Usage: $0 get-metrics <project-key>" >&2; exit 1; }
    do_get_metrics "$1"
    ;;
  get-issues)
    [ $# -ge 2 ] || { echo "Usage: $0 get-issues <project-key> <severity>" >&2; exit 1; }
    do_get_issues "$1" "$2"
    ;;
  resolve-project)
    [ $# -ge 1 ] || { echo "Usage: $0 resolve-project <url-or-key>" >&2; exit 1; }
    do_resolve_project "$1"
    ;;
  *)
    echo "Usage: $0 {setup|test-auth|trigger-analysis|wait-for-analysis|check-quality-gate|get-metrics|get-issues|resolve-project}" >&2
    exit 1
    ;;
esac
