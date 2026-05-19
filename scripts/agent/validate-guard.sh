#!/usr/bin/env bash
# validate-guard.sh — Cursor post-edit hook (afterFileEdit)
#
# Fires after Agent Write/Edit tool calls in Cursor. Warns ONLY when a
# SIGNIFICANT source file change is made outside the execute.md → validate.md
# pipeline.
#
# Significant changes (trigger warning):
#   - New functions, methods, classes, interfaces
#   - Logic changes (if/else, loops, switch, return values, exception handling)
#   - New or changed API endpoints, routes, handlers
#   - New or changed external calls (HTTP, DB, gRPC, queue)
#   - New dependencies (import of new packages)
#   - Config changes that affect runtime behavior
#
# Non-significant changes (silently skip):
#   - Variable/method renames (old and new differ only in identifiers)
#   - Formatting / whitespace-only changes
#   - Comment-only edits
#   - Import reordering (same imports, different order)
#   - Log message text changes (not adding/removing log calls)
#   - Type annotation additions
#   - String literal changes (error messages, display text)
#
# Cursor hook input schema (afterFileEdit):
#   {
#     "file_path": "<absolute path>",
#     "edits": [{ "old_string": "<search>", "new_string": "<replace>" }]
#   }
#
# Install: copy to the target repo as scripts/agent/validate-guard.sh and
# register in .cursor/hooks.json under the `afterFileEdit` event.

set -euo pipefail

# --- Configuration -----------------------------------------------------------
SOURCE_EXTS="java|py|go|ts|tsx|js|jsx|kt|kts|rs|scala|rb|cs"

SKIP_PATTERNS="^docs/|^AGENTS\.md|^ARCHITECTURE\.md|harness-state\.md|_till_done\.json|_execution_plan\.md|_execution_log\.md|^scripts/|^\.claude/|^\.cursor/|^connections\.md|\.md$|\.mdc$|\.json$|\.yaml$|\.yml$|\.xml$|\.properties$|\.toml$|\.cfg$|\.ini$|\.env"

# --- Input -------------------------------------------------------------------
# Cursor hooks receive tool input as JSON on stdin.
INPUT=$(cat)

# Extract file_path — Cursor sends absolute path.
FILE_PATH=$(echo "$INPUT" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('file_path', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# --- Resolve repo-relative path ---------------------------------------------
# Cursor sets CURSOR_PROJECT_DIR; fall back to git toplevel or PWD.
REPO_ROOT="${CURSOR_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
REL_PATH="${FILE_PATH#"$REPO_ROOT/"}"

# --- Check 1: Is this a source file? ----------------------------------------
if ! echo "$REL_PATH" | grep -qE "\.(${SOURCE_EXTS})$"; then
  exit 0
fi

# --- Check 2: Is this in the skip list? --------------------------------------
if echo "$REL_PATH" | grep -qE "$SKIP_PATTERNS"; then
  exit 0
fi

# --- Check 3: Is this a test-only file? --------------------------------------
if echo "$REL_PATH" | grep -qiE "(test|spec|_test\.|\.test\.|\.spec\.|tests/|__tests__/)"; then
  exit 0
fi

# --- Check 4: Is harness-state.md present? -----------------------------------
HARNESS_STATE="${REPO_ROOT}/harness-state.md"
if [ ! -f "$HARNESS_STATE" ]; then
  exit 0
fi

# --- Check 5: Is the pipeline already active? --------------------------------
PIPELINE_STAGE=$(grep -oE 'pipeline-stage:[[:space:]]*[^[:space:]]+' "$HARNESS_STATE" 2>/dev/null \
  | awk -F: '{print $2}' | tr -d '[:space:]' || echo "UNKNOWN")

case "$PIPELINE_STAGE" in
  IMPLEMENTING|STATIC_VALIDATED|VALIDATING)
    exit 0
    ;;
esac

LAST_UPDATER=$(grep -oE 'last-updated-by:[[:space:]]*[^[:space:]]+' "$HARNESS_STATE" 2>/dev/null \
  | awk -F: '{print $2}' | tr -d '[:space:]' || echo "unknown")

case "$LAST_UPDATER" in
  execute|validate|cleanup)
    exit 0
    ;;
esac

# --- Check 6: Is this a significant change? ----------------------------------
# Cursor delivers an `edits` array. We concatenate all old_string / new_string
# values and run the same classifier as the Claude variant. A brand-new file
# (Write tool) arrives as a single edit with empty old_string.
OLD_STRING=$(echo "$INPUT" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    edits = d.get('edits') or []
    print('\n'.join(e.get('old_string', '') for e in edits))
except Exception:
    print('')
" 2>/dev/null || echo "")

NEW_STRING=$(echo "$INPUT" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    edits = d.get('edits') or []
    print('\n'.join(e.get('new_string', '') for e in edits))
except Exception:
    print('')
" 2>/dev/null || echo "")

# New file creation — treat as significant, fall through to warning.
if [ -z "$OLD_STRING" ] && [ -n "$NEW_STRING" ]; then
  :
elif [ -z "$OLD_STRING" ] && [ -z "$NEW_STRING" ]; then
  exit 0
else
  # Edit — classify the change.

  # --- 6A: Whitespace/formatting only ---------------------------------------
  OLD_NORMALIZED=$(echo "$OLD_STRING" | tr -d '[:space:]')
  NEW_NORMALIZED=$(echo "$NEW_STRING" | tr -d '[:space:]')
  if [ "$OLD_NORMALIZED" = "$NEW_NORMALIZED" ]; then
    exit 0
  fi

  # --- 6B: Comment-only changes ---------------------------------------------
  # Comment strip uses a BSD-sed-safe C-comment pattern (GNU sed handles it too).
  OLD_NO_COMMENTS=$(echo "$OLD_STRING" | sed -E '
    s|//.*$||g
    s|#.*$||g
    s|--.*$||g
    s|/\*[^*]*\*+([^/*][^*]*\*+)*/||g
  ' | tr -d '[:space:]')
  NEW_NO_COMMENTS=$(echo "$NEW_STRING" | sed -E '
    s|//.*$||g
    s|#.*$||g
    s|--.*$||g
    s|/\*[^*]*\*+([^/*][^*]*\*+)*/||g
  ' | tr -d '[:space:]')
  if [ "$OLD_NO_COMMENTS" = "$NEW_NO_COMMENTS" ]; then
    exit 0
  fi

  # --- 6C: Import reordering ------------------------------------------------
  OLD_IMPORTS=$(echo "$OLD_STRING" | grep -E '^\s*(import |from .+ import )' | sort 2>/dev/null || echo "")
  NEW_IMPORTS=$(echo "$NEW_STRING" | grep -E '^\s*(import |from .+ import )' | sort 2>/dev/null || echo "")
  OLD_NON_IMPORTS=$(echo "$OLD_STRING" | grep -vE '^\s*(import |from .+ import )' | tr -d '[:space:]' 2>/dev/null || echo "")
  NEW_NON_IMPORTS=$(echo "$NEW_STRING" | grep -vE '^\s*(import |from .+ import )' | tr -d '[:space:]' 2>/dev/null || echo "")
  if [ "$OLD_IMPORTS" = "$NEW_IMPORTS" ] && [ "$OLD_NON_IMPORTS" = "$NEW_NON_IMPORTS" ]; then
    exit 0
  fi
  if [ "$OLD_NON_IMPORTS" = "$NEW_NON_IMPORTS" ] && [ "$OLD_IMPORTS" != "$NEW_IMPORTS" ]; then
    OLD_IMPORT_SET=$(echo "$OLD_IMPORTS" | tr -d '[:space:]' | sort)
    NEW_IMPORT_SET=$(echo "$NEW_IMPORTS" | tr -d '[:space:]' | sort)
    if [ "$OLD_IMPORT_SET" = "$NEW_IMPORT_SET" ]; then
      exit 0
    fi
  fi

  # --- 6D: String literal / log message text only ----------------------------
  OLD_NO_STRINGS=$(echo "$OLD_STRING" | sed -E '
    s/"([^"\\]|\\.)*"/""/g
    s/'\''([^'\''\\]|\\.)*'\''/'\'\''/g
  ' | tr -d '[:space:]')
  NEW_NO_STRINGS=$(echo "$NEW_STRING" | sed -E '
    s/"([^"\\]|\\.)*"/""/g
    s/'\''([^'\''\\]|\\.)*'\''/'\'\''/g
  ' | tr -d '[:space:]')
  if [ "$OLD_NO_STRINGS" = "$NEW_NO_STRINGS" ]; then
    exit 0
  fi

  # --- 6E: Significant structural patterns -----------------------------------
  SIGNIFICANT=false

  if echo "$NEW_STRING" | grep -qE '(public |private |protected |static |def |func |function |class |interface |trait |enum |struct )' 2>/dev/null; then
    OLD_DEFS=$(echo "$OLD_STRING" | grep -cE '(public |private |protected |static |def |func |function |class |interface |trait |enum |struct )' 2>/dev/null || echo "0")
    NEW_DEFS=$(echo "$NEW_STRING" | grep -cE '(public |private |protected |static |def |func |function |class |interface |trait |enum |struct )' 2>/dev/null || echo "0")
    if [ "$NEW_DEFS" -gt "$OLD_DEFS" ]; then
      SIGNIFICANT=true
    fi
  fi

  if [ "$SIGNIFICANT" = false ]; then
    OLD_FLOW=$(echo "$OLD_STRING" | grep -cE '\b(if|else|for|while|switch|case|try|catch|throw|throws|raise|except|finally|return|yield|break|continue)\b' 2>/dev/null || echo "0")
    NEW_FLOW=$(echo "$NEW_STRING" | grep -cE '\b(if|else|for|while|switch|case|try|catch|throw|throws|raise|except|finally|return|yield|break|continue)\b' 2>/dev/null || echo "0")
    if [ "$OLD_FLOW" != "$NEW_FLOW" ]; then
      SIGNIFICANT=true
    fi
  fi

  if [ "$SIGNIFICANT" = false ]; then
    if echo "$NEW_STRING" | grep -qE '(\.execute\(|\.call\(|\.send\(|\.fetch\(|\.request\(|HttpClient|RestTemplate|WebClient|OkHttp|gRPC|\.query\(|\.insert\(|\.update\(|\.delete\(|\.publish\(|\.subscribe\(|@(Get|Post|Put|Delete|Patch)Mapping|@RequestMapping|@Path|@GET|@POST)' 2>/dev/null; then
      SIGNIFICANT=true
    fi
  fi

  if [ "$SIGNIFICANT" = false ]; then
    OLD_ANNOT=$(echo "$OLD_STRING" | grep -cE '^\s*@\w+' 2>/dev/null || echo "0")
    NEW_ANNOT=$(echo "$NEW_STRING" | grep -cE '^\s*@\w+' 2>/dev/null || echo "0")
    if [ "$NEW_ANNOT" -gt "$OLD_ANNOT" ]; then
      SIGNIFICANT=true
    fi
  fi

  if [ "$SIGNIFICANT" = false ]; then
    exit 0
  fi
fi

# --- Emit warning (significant change detected) -----------------------------
FEATURE_TAG=$(grep -oE 'feature-tag:[[:space:]]*[^[:space:]]+' "$HARNESS_STATE" 2>/dev/null \
  | awk -F: '{print $2}' | tr -d '[:space:]' || echo "<unknown>")

cat >&2 <<EOF
VALIDATE GUARD: significant source change outside pipeline
  File:           ${REL_PATH}
  Pipeline stage: ${PIPELINE_STAGE}
  Feature tag:    ${FEATURE_TAG}

  This edit was not made by execute.md and appears to be a significant
  code change (new logic, control flow, external calls, or definitions).

  To ensure runtime validation:
    1. Run execute.md for static gates (build, test, lint, arch)
    2. Run validate.md for runtime gates (Docker, APIs, VictoriaLogs)

  Do NOT declare the task done without execute.md + validate.md passing.

  (Renames, formatting, comments, import reordering, and string
   literal changes do not trigger this warning.)
EOF

exit 0
