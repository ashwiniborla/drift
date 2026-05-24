#!/usr/bin/env bash
# stop.sh — Stop the Drift observability stack and any native JVM processes.
#
# Usage:
#   bash scripts/infra/stop.sh          # stop Docker stack + native processes
#   bash scripts/infra/stop.sh --docker # stop Docker stack only (leave JVM processes)
#   bash scripts/infra/stop.sh --app    # stop native JVM processes only

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PIDS_DIR="/tmp/drift-pids"
MODE="${1:-all}"

echo "=== Stopping Drift Stack ==="

# ─── Docker observability stack ─────────────────────────────────────────────────
if [ "$MODE" != "--app" ]; then
  cd "$REPO_ROOT"
  if docker compose ps -q 2>/dev/null | grep -q .; then
    docker compose down --remove-orphans 2>/dev/null && echo "[ok]  Docker stack stopped." \
      || echo "[warn] docker compose down reported an error (may already be stopped)."
  else
    echo "[info] Docker stack was not running."
  fi
fi

# ─── Native JVM processes ────────────────────────────────────────────────────────
if [ "$MODE" != "--docker" ]; then
  for APP in api worker; do
    PID_FILE="$PIDS_DIR/${APP}.pid"
    if [ -f "$PID_FILE" ]; then
      PID=$(cat "$PID_FILE")
      if kill -0 "$PID" 2>/dev/null; then
        echo "[info] Stopping $APP (PID $PID)..."
        kill "$PID" 2>/dev/null && echo "[ok]  $APP stopped." || echo "[warn] Failed to stop $APP PID $PID."
      else
        echo "[info] $APP PID file exists but process is not running (stale PID $PID)."
      fi
      rm -f "$PID_FILE"
    else
      echo "[info] $APP: not started (no PID file)."
    fi
  done
fi

echo ""
echo "=== Stopped ==="
