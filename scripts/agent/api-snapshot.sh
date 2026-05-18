#!/usr/bin/env bash
# api-snapshot.sh — Capture a point-in-time snapshot of Drift service state
# Usage: bash scripts/agent/api-snapshot.sh

set -euo pipefail

VICTORIALOGS_URL="http://localhost:9428"

echo "=== Drift API Snapshot — $(date -u '+%Y-%m-%dT%H:%M:%SZ') ==="
echo ""

# ─── API Health ───────────────────────────────────────────────────────────────
echo "--- API Health (port 8001) ---"
curl -s --max-time 5 "http://localhost:8001/healthcheck" 2>/dev/null \
  | (command -v jq &>/dev/null && jq . || cat) \
  || echo "UNREACHABLE"
echo ""

# ─── Worker Health ────────────────────────────────────────────────────────────
echo "--- Worker Health (port 7201) ---"
curl -s --max-time 5 "http://localhost:7201/healthcheck" 2>/dev/null \
  | (command -v jq &>/dev/null && jq . || cat) \
  || echo "UNREACHABLE"
echo ""

# ─── Worker Prometheus Metrics Summary ───────────────────────────────────────
echo "--- Worker Prometheus Metrics (port 9090) ---"
curl -s --max-time 5 "http://localhost:9090/metrics" 2>/dev/null \
  | grep -E "^(workflow_|temporal_workflow_task|temporal_activity)" \
  | head -20 \
  || echo "UNREACHABLE"
echo ""

# ─── Recent Logs from VictoriaLogs ────────────────────────────────────────────
echo "--- Recent Logs (last 20 lines from VictoriaLogs) ---"
curl -s -G "$VICTORIALOGS_URL/select/logsql/query" \
  --data-urlencode "query=*" \
  --data-urlencode "limit=20" 2>/dev/null \
  | (command -v jq &>/dev/null \
      && jq -r '"\(.["_time"] // "") [\(.service // "?")] \(.["_msg"] // "")"' \
      || cat) \
  || echo "VictoriaLogs UNREACHABLE"
echo ""

echo "=== Snapshot complete ==="
