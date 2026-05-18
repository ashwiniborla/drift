#!/usr/bin/env bash
# health.sh — Check health of Drift API or Worker
# Usage:
#   bash scripts/agent/health.sh api
#   bash scripts/agent/health.sh worker
#   bash scripts/agent/health.sh all

set -euo pipefail

APP="${1:-all}"

check_health() {
  local service="$1"
  local port="$2"
  local url="http://localhost:$port/healthcheck"
  local result

  result=$(curl -s --max-time 5 "$url" 2>/dev/null || echo "UNREACHABLE")

  if echo "$result" | grep -q '"healthy":true'; then
    echo "[ok] $service is healthy ($url)"
    return 0
  elif [ "$result" = "UNREACHABLE" ]; then
    echo "[fail] $service is not reachable at $url" >&2
    return 1
  else
    echo "[fail] $service health check failed at $url" >&2
    echo "       Response: $result" >&2
    return 1
  fi
}

FAILED=0

case "$APP" in
  api)
    check_health "api" 8001 || FAILED=1
    ;;
  worker)
    check_health "worker" 7201 || FAILED=1
    ;;
  all)
    check_health "api" 8001 || FAILED=1
    check_health "worker" 7201 || FAILED=1
    ;;
  *)
    echo "Usage: bash scripts/agent/health.sh [api|worker|all]"
    exit 1
    ;;
esac

if [ $FAILED -eq 0 ]; then
  echo "All health checks passed."
  exit 0
else
  echo "One or more health checks failed." >&2
  exit 1
fi
