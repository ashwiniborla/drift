#!/usr/bin/env bash
# start.sh — Start Drift observability stack (Vector + VictoriaLogs) via Docker Compose
# app-runtime: local — only observability services run in Docker
# Usage: bash scripts/infra/start.sh

set -euo pipefail

REPO_ROOT="/Users/nidhi.b/IdeaProjects/drift"

# Pre-flight
bash "$REPO_ROOT/scripts/agent/check-prereq.sh"

echo "=== Starting Drift Observability Stack ==="
echo "  Mode: local (app runs natively; observability in Docker)"
echo ""

# Ensure log directory exists for Vector to tail
mkdir -p /tmp/drift-logs

cd "$REPO_ROOT"

# Boot only Vector + VictoriaLogs (not the app services)
docker compose up -d victorialogs vector

echo ""
echo "=== Observability stack started ==="
echo "  VictoriaLogs: http://localhost:9428"
echo "  Vector:       running (shipping logs from /tmp/drift-logs/*.log)"
echo ""
echo "Next steps:"
echo "  APP=api    bash scripts/agent/boot.sh   # start API service"
echo "  APP=worker bash scripts/agent/boot.sh   # start Worker service"
