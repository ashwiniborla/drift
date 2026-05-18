#!/usr/bin/env bash
# db-snapshot.sh — Verify data store connectivity for Drift
# Usage: bash scripts/agent/db-snapshot.sh

set -euo pipefail

echo "=== Drift DB/Cache Connectivity Snapshot — $(date -u '+%Y-%m-%dT%H:%M:%SZ') ==="
echo ""

# ─── HBase (indirect via Worker health) ──────────────────────────────────────
echo "--- HBase connectivity (via Worker health endpoint) ---"
WORKER_HEALTH=$(curl -s --max-time 5 "http://localhost:7201/healthcheck" 2>/dev/null || echo "UNREACHABLE")
if echo "$WORKER_HEALTH" | grep -q '"healthy":true'; then
  echo "[ok] Worker is healthy — implies HBase connection is established"
elif [ "$WORKER_HEALTH" = "UNREACHABLE" ]; then
  echo "[warn] Worker is not running — cannot check HBase connectivity"
else
  echo "[warn] Worker health check returned: $WORKER_HEALTH"
fi
echo ""

# ─── Redis (check if REDIS_SENTINELS is set) ─────────────────────────────────
echo "--- Redis Sentinel connectivity ---"
if [ -z "${REDIS_SENTINELS:-}" ]; then
  echo "[warn] REDIS_SENTINELS not set in environment — cannot check Redis"
else
  # Attempt a TCP connection to the first sentinel
  FIRST_SENTINEL=$(echo "$REDIS_SENTINELS" | cut -d',' -f1)
  SENTINEL_HOST=$(echo "$FIRST_SENTINEL" | cut -d':' -f1)
  SENTINEL_PORT=$(echo "$FIRST_SENTINEL" | cut -d':' -f2)
  if nc -z -w 3 "$SENTINEL_HOST" "$SENTINEL_PORT" 2>/dev/null; then
    echo "[ok] Redis sentinel reachable at $FIRST_SENTINEL"
  else
    echo "[fail] Redis sentinel NOT reachable at $FIRST_SENTINEL" >&2
  fi
fi
echo ""

# ─── Temporal Frontend ────────────────────────────────────────────────────────
echo "--- Temporal Frontend connectivity ---"
if [ -z "${TEMPORAL_FRONTEND:-}" ]; then
  echo "[warn] TEMPORAL_FRONTEND not set in environment"
else
  TEMPORAL_HOST=$(echo "$TEMPORAL_FRONTEND" | cut -d':' -f1)
  TEMPORAL_PORT=$(echo "$TEMPORAL_FRONTEND" | cut -d':' -f2)
  if nc -z -w 3 "$TEMPORAL_HOST" "${TEMPORAL_PORT:-7233}" 2>/dev/null; then
    echo "[ok] Temporal frontend reachable at $TEMPORAL_FRONTEND"
  else
    echo "[fail] Temporal frontend NOT reachable at $TEMPORAL_FRONTEND" >&2
  fi
fi
echo ""

echo "=== DB snapshot complete ==="
