#!/usr/bin/env bash
# stop.sh — Stop the Drift observability stack
# Usage: bash scripts/infra/stop.sh

set -euo pipefail

REPO_ROOT="/Users/nidhi.b/IdeaProjects/drift"
PIDS_DIR="/tmp/drift-pids"

echo "=== Stopping Drift Observability Stack ==="

cd "$REPO_ROOT"
docker compose down 2>/dev/null || echo "[warn] Docker compose down failed or not running"

# Also kill any native JVM processes we started
for APP in api worker; do
  PID_FILE="$PIDS_DIR/${APP}.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "Stopping $APP (PID $PID)..."
      kill "$PID" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
  fi
done

echo "=== All stopped ==="
