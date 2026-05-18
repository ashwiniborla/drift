#!/usr/bin/env bash
# query-logs.sh — Query logs from VictoriaLogs
# Usage:
#   bash scripts/agent/query-logs.sh "<query>" [limit] [service]
# Examples:
#   bash scripts/agent/query-logs.sh "error" 50
#   bash scripts/agent/query-logs.sh "WORKFLOW_FAILED" 20 worker
#   bash scripts/agent/query-logs.sh "WorkflowResource" 10 api

set -euo pipefail

VICTORIALOGS_URL="http://localhost:9428"
QUERY="${1:-*}"
LIMIT="${2:-50}"
SERVICE="${3:-}"

if [ -n "$SERVICE" ]; then
  FULL_QUERY="service:${SERVICE} AND ${QUERY}"
else
  FULL_QUERY="$QUERY"
fi

echo "=== Querying VictoriaLogs ==="
echo "Query:  $FULL_QUERY"
echo "Limit:  $LIMIT"
echo ""

RESPONSE=$(curl -s -G "$VICTORIALOGS_URL/select/logsql/query" \
  --data-urlencode "query=$FULL_QUERY" \
  --data-urlencode "limit=$LIMIT" 2>/dev/null || echo "UNREACHABLE")

if [ "$RESPONSE" = "UNREACHABLE" ]; then
  echo "[error] VictoriaLogs is not reachable at $VICTORIALOGS_URL" >&2
  echo "        Start the observability stack: bash scripts/infra/start.sh" >&2
  exit 1
fi

if [ -z "$RESPONSE" ]; then
  echo "(no results)"
else
  # Pretty-print each JSON log line
  echo "$RESPONSE" | while IFS= read -r line; do
    if [ -n "$line" ]; then
      # Try jq if available, otherwise raw
      if command -v jq &>/dev/null; then
        echo "$line" | jq -r '"\(.["_time"] // "") [\(.service // "?")][\(.["_stream"] // "")] \(.["_msg"] // .msg // .message // .)"' 2>/dev/null || echo "$line"
      else
        echo "$line"
      fi
    fi
  done
fi
