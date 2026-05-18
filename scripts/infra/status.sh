#!/usr/bin/env bash
# status.sh — Show status of Drift services and observability stack
# Usage: bash scripts/infra/status.sh

set -euo pipefail

REPO_ROOT="/Users/nidhi.b/IdeaProjects/drift"
PIDS_DIR="/tmp/drift-pids"

echo "=== Drift Stack Status — $(date '+%Y-%m-%d %H:%M:%S') ==="
echo ""

# ─── Docker containers ────────────────────────────────────────────────────────
echo "--- Docker Containers ---"
cd "$REPO_ROOT"
docker compose ps 2>/dev/null || echo "(docker compose not available or not started)"
echo ""

# ─── Native processes ─────────────────────────────────────────────────────────
echo "--- Native JVM Processes ---"
for APP in api worker; do
  PID_FILE="$PIDS_DIR/${APP}.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "  $APP: RUNNING (PID $PID)"
    else
      echo "  $APP: STOPPED (stale PID file)"
    fi
  else
    echo "  $APP: NOT STARTED"
  fi
done
echo ""

# ─── Health checks ────────────────────────────────────────────────────────────
echo "--- Health Checks ---"
bash "$REPO_ROOT/scripts/agent/health.sh" all 2>/dev/null || true
echo ""

# ─── Ports in use ─────────────────────────────────────────────────────────────
echo "--- Ports in use ---"
for PORT in 8000 8001 7200 7201 9090 9428; do
  if lsof -Pi ":$PORT" -sTCP:LISTEN -t &>/dev/null; then
    PID_USING=$(lsof -Pi ":$PORT" -sTCP:LISTEN -t 2>/dev/null | head -1)
    echo "  :$PORT — IN USE (PID $PID_USING)"
  else
    echo "  :$PORT — free"
  fi
done
