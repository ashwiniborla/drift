#!/usr/bin/env bash
# verify-pipeline.sh — End-to-end health verification of the Drift stack.
#
# Exit codes:
#   0 — all checked components are healthy (app checks are skipped if not running)
#   1 — at least one infrastructure component (VictoriaLogs, Vector) is unhealthy
#
# App health failures (API, Worker) are reported as warnings, not errors, because
# the app is a native JVM process and may not be started yet. Infrastructure failures
# are fatal: if VictoriaLogs is down the validate-loop cannot read logs.
#
# Usage: bash scripts/agent/verify-pipeline.sh

set -uo pipefail

VICTORIALOGS_URL="http://localhost:9428"
INFRA_FAIL=0   # failures in infrastructure components (VictoriaLogs, Vector) — exit 1
APP_WARN=0     # app process not running — warn only, not a pipeline failure

echo "=== Drift Pipeline Verification — $(date -u '+%Y-%m-%dT%H:%M:%SZ') ==="
echo ""

# ─── 1. API health (native JVM process — warn if not running) ─────────────────
echo "[1/5] Checking API health..."
if curl -s --max-time 5 "http://localhost:8001/healthcheck" 2>/dev/null | grep -q '"healthy":true'; then
  echo "      [ok]   API is healthy"
else
  echo "      [warn] API not responding (not started, or still booting)"
  APP_WARN=1
fi

# ─── 2. Worker health (native JVM process — warn if not running) ──────────────
echo "[2/5] Checking Worker health..."
if curl -s --max-time 5 "http://localhost:7201/healthcheck" 2>/dev/null | grep -q '"healthy":true'; then
  echo "      [ok]   Worker is healthy"
else
  echo "      [warn] Worker not responding (not started, or still booting)"
  APP_WARN=1
fi

# ─── 3. VictoriaLogs reachability (REQUIRED — fail if down) ───────────────────
echo "[3/5] Checking VictoriaLogs..."
VL_HEALTH=$(curl -s --max-time 5 "$VICTORIALOGS_URL/health" 2>/dev/null || echo "UNREACHABLE")
if echo "$VL_HEALTH" | grep -qi "OK"; then
  echo "      [ok]   VictoriaLogs is healthy at $VICTORIALOGS_URL"
else
  # Try the query endpoint as a secondary check
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "$VICTORIALOGS_URL/select/logsql/query?query=*&limit=1" 2>/dev/null || echo "000")
  if [ "$STATUS" = "200" ]; then
    echo "      [ok]   VictoriaLogs reachable (query endpoint responded HTTP 200)"
  else
    echo "      [FAIL] VictoriaLogs NOT reachable at $VICTORIALOGS_URL (HTTP $STATUS)" >&2
    echo "             Start the stack: bash scripts/infra/start.sh" >&2
    INFRA_FAIL=1
  fi
fi

# ─── 4. Log pipeline: write a test entry and confirm it appears in VictoriaLogs ─
echo "[4/5] Testing log pipeline (file -> Vector -> VictoriaLogs)..."
if [ "$INFRA_FAIL" -eq 0 ]; then
  PROBE_ID="probe-$(date +%s)"
  LOG_DIR="/private/tmp/drift-logs"
  PROBE_FILE="$LOG_DIR/api.log"

  # Only write the probe if the log dir is available
  if [ -d "$LOG_DIR" ]; then
    echo "{\"level\":\"INFO\",\"message\":\"$PROBE_ID\",\"service\":\"api\"}" >> "$PROBE_FILE"
    # Allow Vector up to 8s to tail, transform, and ship the entry
    FOUND=0
    for i in $(seq 1 4); do
      sleep 2
      RESULT=$(curl -sf "$VICTORIALOGS_URL/select/logsql/query" \
        --data-urlencode "query=_msg:~\"$PROBE_ID\"" \
        --data-urlencode "limit=1" 2>/dev/null || echo "")
      if echo "$RESULT" | grep -q "$PROBE_ID"; then
        FOUND=1
        break
      fi
    done
    if [ "$FOUND" -eq 1 ]; then
      echo "      [ok]   Probe log entry reached VictoriaLogs (pipeline is live)"
    else
      echo "      [FAIL] Probe log entry NOT found in VictoriaLogs after 8s" >&2
      echo "             Check Vector: docker compose logs vector" >&2
      INFRA_FAIL=1
    fi
  else
    echo "      [warn] Log directory $LOG_DIR not found — skipping probe test"
    echo "             Run bash scripts/infra/start.sh to create it"
    APP_WARN=1
  fi
else
  echo "      [skip] Skipping pipeline probe — VictoriaLogs is not reachable"
fi

# ─── 5. Temporal reachability (warn only if not set) ──────────────────────────
echo "[5/5] Checking Temporal frontend connectivity..."
if [ -z "${TEMPORAL_FRONTEND:-}" ]; then
  echo "      [warn] TEMPORAL_FRONTEND not set — skipping Temporal check"
  APP_WARN=1
else
  TEMPORAL_HOST=$(echo "$TEMPORAL_FRONTEND" | cut -d':' -f1)
  TEMPORAL_PORT=$(echo "$TEMPORAL_FRONTEND" | cut -d':' -f2)
  if nc -z -w 3 "$TEMPORAL_HOST" "${TEMPORAL_PORT:-7233}" 2>/dev/null; then
    echo "      [ok]   Temporal frontend reachable at $TEMPORAL_FRONTEND"
  else
    echo "      [FAIL] Temporal frontend NOT reachable at $TEMPORAL_FRONTEND" >&2
    INFRA_FAIL=1
  fi
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
if [ "$INFRA_FAIL" -eq 0 ] && [ "$APP_WARN" -eq 0 ]; then
  echo "PIPELINE VERIFICATION PASSED — all components healthy."
  exit 0
elif [ "$INFRA_FAIL" -eq 0 ]; then
  echo "PIPELINE VERIFICATION PASSED — infrastructure healthy."
  echo "  (some app components are not running — start with scripts/agent/boot.sh)"
  exit 0
else
  echo "PIPELINE VERIFICATION FAILED — infrastructure is unhealthy." >&2
  echo "  Fix infrastructure issues before running the app." >&2
  exit 1
fi
