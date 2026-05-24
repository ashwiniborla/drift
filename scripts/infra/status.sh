#!/usr/bin/env bash
# status.sh — Show status of the Drift stack: Docker containers, native processes,
#             health endpoints, log pipeline, and ports.
#
# Usage: bash scripts/infra/status.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PIDS_DIR="/tmp/drift-pids"
LOG_DIR="/private/tmp/drift-logs"

echo "=== Drift Stack Status — $(date '+%Y-%m-%d %H:%M:%S') ==="
echo ""

# ─── Docker containers ───────────────────────────────────────────────────────────
echo "--- Docker Containers ---"
cd "$REPO_ROOT"
docker compose ps 2>/dev/null || echo "  (docker compose not available or not started)"
echo ""

# ─── Native JVM processes ────────────────────────────────────────────────────────
echo "--- Native JVM Processes ---"
for APP in api worker; do
  PID_FILE="$PIDS_DIR/${APP}.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "  $APP: RUNNING (PID $PID)"
    else
      echo "  $APP: STOPPED (stale PID file at $PID_FILE)"
    fi
  else
    echo "  $APP: NOT STARTED"
  fi
done
echo ""

# ─── Health checks ───────────────────────────────────────────────────────────────
echo "--- Health Checks ---"
bash "$REPO_ROOT/scripts/agent/health.sh" all 2>/dev/null || true
echo ""

# ─── Log pipeline status ─────────────────────────────────────────────────────────
echo "--- Log Pipeline ---"
echo "  Log directory: $LOG_DIR"
for SVC in api worker; do
  LOG_FILE="$LOG_DIR/${SVC}.log"
  if [ -f "$LOG_FILE" ]; then
    LINE_COUNT=$(wc -l < "$LOG_FILE" 2>/dev/null | tr -d ' ')
    SIZE=$(du -sh "$LOG_FILE" 2>/dev/null | cut -f1)
    MODIFIED=$(stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' "$LOG_FILE" 2>/dev/null || stat -c '%y' "$LOG_FILE" 2>/dev/null | cut -c1-19 || echo "?")
    echo "  $LOG_FILE: $LINE_COUNT lines, $SIZE, last modified $MODIFIED"
  else
    echo "  $LOG_FILE: NOT FOUND (app not started or not logging yet)"
  fi
done
VLOGS_COUNT=$(curl -sf --max-time 3 \
  "http://localhost:9428/select/logsql/query?query=*&limit=1" 2>/dev/null | wc -l | tr -d ' ')
if [ "${VLOGS_COUNT:-0}" -gt 0 ]; then
  echo "  VictoriaLogs: receiving logs (query returned results)"
else
  VL_STATUS=$(curl -sf --max-time 3 "http://localhost:9428/health" 2>/dev/null || echo "UNREACHABLE")
  echo "  VictoriaLogs: $VL_STATUS (no log entries yet — start the app and wait)"
fi
echo ""

# ─── Ports in use ────────────────────────────────────────────────────────────────
echo "--- Ports ---"
for PORT in 8000 8001 7200 7201 9428; do
  LABEL=""
  case "$PORT" in
    8000) LABEL="api (app)" ;;
    8001) LABEL="api (admin/management)" ;;
    7200) LABEL="worker (app)" ;;
    7201) LABEL="worker (admin/management)" ;;
    9428) LABEL="VictoriaLogs" ;;
  esac
  if lsof -Pi ":$PORT" -sTCP:LISTEN -t &>/dev/null 2>&1; then
    PID_USING=$(lsof -Pi ":$PORT" -sTCP:LISTEN -t 2>/dev/null | head -1)
    echo "  :$PORT  IN USE  — $LABEL (PID $PID_USING)"
  else
    echo "  :$PORT  free    — $LABEL"
  fi
done
echo ""
