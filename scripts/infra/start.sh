#!/usr/bin/env bash
# start.sh — Start the Drift observability stack (Vector + VictoriaLogs) via Docker Compose.
# app-runtime: local — only observability services run in Docker.
# The API and Worker run as native JVM processes via scripts/agent/boot.sh.
#
# Usage:
#   bash scripts/infra/start.sh           # start (or restart) the stack
#   bash scripts/infra/start.sh --reset   # wipe log volume + restart (clears stored logs)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESET="${1:-}"

# ─── Pre-flight ─────────────────────────────────────────────────────────────────
bash "$REPO_ROOT/scripts/agent/check-prereq.sh"

docker info > /dev/null 2>&1 || {
  echo "[error] Docker is not running. Start Docker Desktop first."
  exit 1
}

# ─── Log directory ──────────────────────────────────────────────────────────────
# On macOS, /tmp is a symlink to /private/tmp. Docker Desktop requires the real
# (non-symlink) path for bind mounts to work. docker-compose.yml mounts
# /private/tmp/drift-logs, so we create that directory here.
LOG_DIR="/private/tmp/drift-logs"
mkdir -p "$LOG_DIR"
echo "[info] Log directory ready: $LOG_DIR"

# ─── Optional reset ─────────────────────────────────────────────────────────────
if [ "$RESET" = "--reset" ]; then
  echo "[warn] --reset: removing VictoriaLogs volume and stored logs in 5s... Ctrl+C to cancel."
  sleep 5
  cd "$REPO_ROOT"
  docker compose down -v --remove-orphans 2>/dev/null || true
  echo "[info] Volume removed."
fi

echo ""
echo "=== Starting Drift Observability Stack ==="
echo "  Mode: local (API + Worker run natively; observability in Docker)"
echo ""

cd "$REPO_ROOT"

# ─── Pipeline-first: bring up VictoriaLogs before Vector ────────────────────────
# Vector depends_on victorialogs (service_healthy), so compose handles ordering,
# but we also wait here so the health endpoint is confirmed before we return.
docker compose up -d victorialogs

echo "[info] Waiting for VictoriaLogs to be healthy..."
SECONDS_WAITED=0
until curl -sf --max-time 3 "http://localhost:9428/health" | grep -q "OK" 2>/dev/null; do
  if [ "$SECONDS_WAITED" -ge 60 ]; then
    echo "[error] VictoriaLogs did not become healthy within 60s."
    docker compose logs victorialogs | tail -20
    exit 1
  fi
  sleep 2
  SECONDS_WAITED=$((SECONDS_WAITED + 2))
done
echo "[ok]   VictoriaLogs healthy at http://localhost:9428"

# ─── Start Vector (depends_on ensures VictoriaLogs is healthy first) ─────────
docker compose up -d vector

echo "[info] Waiting for Vector to start..."
SECONDS_WAITED=0
until docker inspect drift-vector --format '{{.State.Running}}' 2>/dev/null | grep -q "true"; do
  if [ "$SECONDS_WAITED" -ge 30 ]; then
    echo "[error] Vector container did not start within 30s."
    docker compose logs vector | tail -20
    exit 1
  fi
  sleep 2
  SECONDS_WAITED=$((SECONDS_WAITED + 2))
done
echo "[ok]   Vector running (tailing $LOG_DIR/*.log)"

echo ""
echo "=== Observability stack ready ==="
echo ""
echo "  VictoriaLogs:  http://localhost:9428"
echo "  Log directory: $LOG_DIR"
echo "  Log pipeline:  $LOG_DIR/*.log -> Vector -> VictoriaLogs"
echo ""
echo "Query logs:"
echo "  bash scripts/agent/query-logs.sh 'service:api'"
echo "  bash scripts/agent/query-logs.sh 'level:ERROR'"
echo "  bash scripts/agent/verify-pipeline.sh"
echo ""
echo "Start app services:"
echo "  APP=api    bash scripts/agent/boot.sh"
echo "  APP=worker bash scripts/agent/boot.sh"
