#!/usr/bin/env bash
# prompt-router.sh — Harness prompt routing hook
#
# Claude Code: UserPromptSubmit — fires before Claude processes every user message.
#              Output to stdout is injected into the prompt context.
#
# Cursor:      beforeSubmitPrompt — fires right after user hits send.
#              Must output JSON: {"continue": true, "user_message": "..."}
#              CURSOR_VERSION env var is always set in Cursor hook invocations.
#
# Behaviour:
#   - No harness-state.md present → silent no-op (non-harness repos unaffected)
#   - harness-state.md present    → injects routing instruction with current
#     pipeline-stage and feature-tag so the agent routes through harness-setup
#     instead of implementing directly.
#
# Install: copied to scripts/agent/prompt-router.sh by install.sh.
# Wired into:
#   Claude Code — .claude/settings.json  → hooks.UserPromptSubmit
#   Cursor      — .cursor/hooks.json     → hooks.beforeSubmitPrompt

set -euo pipefail

INPUT=$(cat)

# --- Resolve repo root from hook payload, fall back to git -------------------
REPO_ROOT=$(python3 -c "
import json, sys
try:
    d = json.loads(sys.stdin.read())
    roots = d.get('workspace_roots') or []
    print(roots[0]) if roots else print('')
except:
    print('')
" <<< "$INPUT" 2>/dev/null || echo "")

[ -z "$REPO_ROOT" ] && REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)

HARNESS_STATE="${REPO_ROOT}/harness-state.md"

# No harness-state.md — not a harness repo, no-op
if [ ! -f "$HARNESS_STATE" ]; then
  [ -n "${CURSOR_VERSION:-}" ] && echo '{"continue": true}' || true
  exit 0
fi

# --- Read pipeline state -----------------------------------------------------
PIPELINE_STAGE=$(awk -F': ' '/^pipeline-stage:/{gsub(/[[:space:]]/, "", $2); print $2; exit}' "$HARNESS_STATE" 2>/dev/null || echo "UNINITIALIZED")
FEATURE_TAG=$(awk    -F': ' '/^feature-tag:/{gsub(/[[:space:]]/, "", $2); print $2; exit}'    "$HARNESS_STATE" 2>/dev/null || echo "")
REPO_TYPE=$(awk      -F': ' '/^repo-type:/{gsub(/[[:space:]]/, "", $2); print $2; exit}'      "$HARNESS_STATE" 2>/dev/null || echo "")

# --- Build context line -------------------------------------------------------
CONTEXT="pipeline-stage: ${PIPELINE_STAGE}"
[ -n "$FEATURE_TAG" ] && CONTEXT="${CONTEXT}, feature: ${FEATURE_TAG}"
[ -n "$REPO_TYPE"   ] && CONTEXT="${CONTEXT}, repo-type: ${REPO_TYPE}"

MSG="[HARNESS ACTIVE] ${CONTEXT}
Route ANY coding request (feature, bug fix, refactor, config change) through the harness-setup agent FIRST. Do not implement directly. Read harness-state.md pipeline-stage before acting."

# --- Emit in editor-appropriate format ----------------------------------------
if [ -n "${CURSOR_VERSION:-}" ]; then
  # Cursor: beforeSubmitPrompt expects JSON output
  python3 -c "
import json, sys
print(json.dumps({'continue': True, 'user_message': sys.argv[1]}))
" "$MSG"
else
  # Claude Code: UserPromptSubmit — plain text on stdout is injected into context
  echo "$MSG"
fi
