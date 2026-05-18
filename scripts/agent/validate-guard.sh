#!/usr/bin/env bash
# validate-guard.sh — Warn when source files are edited outside the execute.md → validate.md pipeline
# Fires after every Edit/Write tool call on source files.
# Significant changes trigger a warning; trivial edits are silently skipped.

set -uo pipefail

REPO_ROOT="/Users/nidhi.b/IdeaProjects/drift"
STATE_FILE="$REPO_ROOT/harness-state.md"

# ─── Skip if pipeline is active ───────────────────────────────────────────────
if [ -f "$STATE_FILE" ]; then
  PIPELINE_STAGE=$(grep -oP 'pipeline-stage:\s*\K\S+' "$STATE_FILE" 2>/dev/null || echo "")
  LAST_UPDATED=$(grep -oP 'last-updated-by:\s*\K\S+' "$STATE_FILE" 2>/dev/null || echo "")

  # Pipeline is actively implementing or validating — skip warning
  if [[ "$PIPELINE_STAGE" =~ ^(IMPLEMENTING|STATIC_VALIDATED|VALIDATING|VALIDATED|CLEANING_UP)$ ]]; then
    exit 0
  fi

  # A pipeline agent is writing this file — skip warning
  if [[ "$LAST_UPDATED" =~ ^(execute|validate|cleanup)$ ]]; then
    exit 0
  fi
fi

# ─── Get the file being edited (passed as $1 if available) ────────────────────
EDITED_FILE="${1:-}"

# ─── Skip non-source files ────────────────────────────────────────────────────
if [ -n "$EDITED_FILE" ]; then
  # Only care about Java source files
  case "$EDITED_FILE" in
    *.java|*.kt|*.go|*.py|*.ts|*.js|*.scala)
      # Source file — continue to significance check
      ;;
    *)
      # Config, docs, YAML, JSON, Markdown, shell scripts — not significant
      exit 0
      ;;
  esac
fi

# ─── Check if the change is significant ──────────────────────────────────────
# Get the git diff to check what changed
if [ -n "$EDITED_FILE" ] && command -v git &>/dev/null; then
  DIFF=$(git diff -- "$EDITED_FILE" 2>/dev/null || echo "")
  DIFF_STAT=$(git diff --stat -- "$EDITED_FILE" 2>/dev/null || echo "")

  if [ -z "$DIFF" ]; then
    # File is new (untracked) — always significant
    IS_SIGNIFICANT=true
  else
    # Check for significant change patterns
    if echo "$DIFF" | grep -qE '^\+.*(public |private |protected |void |return |throw |if\s*\(|for\s*\(|while\s*\(|switch\s*\(|catch\s*\(|@[A-Z]|new [A-Z]|import .*\.(http|jdbc|redis|hbase|temporal))'; then
      IS_SIGNIFICANT=true
    else
      IS_SIGNIFICANT=false
    fi
  fi
else
  # Cannot determine — assume significant
  IS_SIGNIFICANT=true
fi

if [ "$IS_SIGNIFICANT" = "false" ]; then
  exit 0
fi

# ─── Emit warning ─────────────────────────────────────────────────────────────
cat >&2 <<'EOF'

  ╔══════════════════════════════════════════════════════════════════════════╗
  ║  VALIDATE-GUARD WARNING                                                  ║
  ║                                                                          ║
  ║  A source file was modified outside the execute.md → validate.md        ║
  ║  pipeline. This change was NOT validated by:                             ║
  ║    - Static gates (build, tests, lint, arch)                             ║
  ║    - Runtime validation (Docker stack, API probes, VictoriaLogs)         ║
  ║                                                                          ║
  ║  Options:                                                                ║
  ║    A) Route through the pipeline (recommended):                          ║
  ║       Ask the harness to "run this change through execute → validate"    ║
  ║    B) Accept the risk and continue (not recommended):                    ║
  ║       Changes will be unvalidated until the next pipeline run            ║
  ╚══════════════════════════════════════════════════════════════════════════╝

EOF

exit 0  # warning only — do not block the edit
