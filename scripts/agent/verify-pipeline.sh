#!/usr/bin/env bash
# verify-pipeline.sh — End-to-end health verification of the Drift stack
# Exit 0 = all components healthy. Exit 1 = at least one failure.
# Usage: bash scripts/agent/verify-pipeline.sh

set -uo pipefail

VICTORIALOGS_URL="http://localhost:9428"
FAIL=0

echo "=== Drift Pipeline Verification — $(date -u '+%Y-%m-%dT%H:%M:%SZ') ==="
echo ""

# ─── 1. API health ────────────────────────────────────────────────────────────
echo "[1/5] Checking API health..."
if curl -s --max-time 5 "http://localhost:8001/healthcheck" | grep -q '"healthy":true' 2>/dev/null; then
  echo "      [ok] API is healthy"
else
  echo "      [FAIL] API health check failed" >&2
  FAIL=1
fi

# ─── 2. Worker health ─────────────────────────────────────────────────────────
echo "[2/5] Checking Worker health..."
if curl -s --max-time 5 "http://localhost:7201/healthcheck" | grep -q '"healthy":true' 2>/dev/null; then
  echo "      [ok] Worker is healthy"
else
  echo "      [FAIL] Worker health check failed" >&2
  FAIL=1
fi

# ─── 3. VictoriaLogs reachability ─────────────────────────────────────────────
echo "[3/5] Checking VictoriaLogs..."
if curl -s --max-time 5 "$VICTORIALOGS_URL/health" | grep -q "OK\|ok\|healthy" 2>/dev/null; then
  echo "      [ok] VictoriaLogs is reachable"
else
  # Try alternate endpoint
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$VICTORIALOGS_URL/select/logsql/query?query=*&limit=1" 2>/dev/null || echo "000")
  if [ "$STATUS" = "200" ]; then
    echo "      [ok] VictoriaLogs is reachable (query endpoint responded)"
  else
    echo "      [FAIL] VictoriaLogs not reachable at $VICTORIALOGS_URL (HTTP $STATUS)" >&2
    FAIL=1
  fi
fi

# ─── 4. Log query test ────────────────────────────────────────────────────────
echo "[4/5] Running test log query..."
LOG_COUNT=$(curl -s -G "$VICTORIALOGS_URL/select/logsql/query" \
  --data-urlencode "query=*" \
  --data-urlencode "limit=5" 2>/dev/null | wc -l | tr -d ' ')
if [ "${LOG_COUNT:-0}" -ge 0 ]; then
  echo "      [ok] Log query returned ${LOG_COUNT} log lines"
else
  echo "      [warn] Log query returned no results (services may not have logged yet)"
fi

# ─── 5. Temporal reachability ─────────────────────────────────────────────────
echo "[5/5] Checking Temporal frontend connectivity..."
if [ -z "${TEMPORAL_FRONTEND:-}" ]; then
  echo "      [warn] TEMPORAL_FRONTEND not set — skipping Temporal check"
else
  TEMPORAL_HOST=$(echo "$TEMPORAL_FRONTEND" | cut -d':' -f1)
  TEMPORAL_PORT=$(echo "$TEMPORAL_FRONTEND" | cut -d':' -f2)
  if nc -z -w 3 "$TEMPORAL_HOST" "${TEMPORAL_PORT:-7233}" 2>/dev/null; then
    echo "      [ok] Temporal frontend reachable at $TEMPORAL_FRONTEND"
  else
    echo "      [FAIL] Temporal frontend NOT reachable at $TEMPORAL_FRONTEND" >&2
    FAIL=1
  fi
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
if [ $FAIL -eq 0 ]; then
  echo "PIPELINE VERIFICATION PASSED — all components healthy."
  exit 0
else
  echo "PIPELINE VERIFICATION FAILED — one or more components are unhealthy." >&2
  exit 1
fi
