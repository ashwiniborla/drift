# APP_LEGIBILITY.md — Drift Service Instrumentation Guide

## Overview

This document describes how the harness observes the Drift services during local development and validation.
Both `api` and `worker` are instrumented for structured logging, health checking, log forwarding, and
API/DB snapshots.

## Structured Logging

Both services use SLF4J with Logback (via Dropwizard). The log format includes MDC request ID:
```
%date %level [%thread] %logger{0} [%X{id}] %msg%n
```

Log output goes to **stdout** (console appender). Vector picks up stdout/stderr from the native processes
and forwards to VictoriaLogs.

**Log levels:**
- API: `INFO` for `com.flipkart.drift`, `INFO` default
- Worker: Inherits Dropwizard defaults; workflow code uses `Workflow.getLogger()` for deterministic logging

## Health Endpoints

| Service | URL | Expected |
|---------|-----|----------|
| API | `http://localhost:8001/healthcheck` | HTTP 200, `{"deadlocks":{"healthy":true}}` |
| Worker | `http://localhost:7201/healthcheck` | HTTP 200, `{"deadlocks":{"healthy":true}}` |

## Metrics Endpoint

Worker exposes Prometheus at `http://localhost:9090/metrics`.

Key metric names:
- `workflow_start_duration_seconds` — histogram from `@Timed`
- `workflow_resume_duration_seconds` — histogram from `@Timed`
- Temporal SDK metrics are prefixed `temporal_`

## Log Forwarding (Vector → VictoriaLogs)

The `docker-compose.yml` for local mode runs only Vector and VictoriaLogs.
Vector is configured to:
1. Tail the process stdout via a named pipe or file sink from `boot.sh`
2. Add labels: `service=api` or `service=worker`, `env=local`
3. Forward to VictoriaLogs at `http://localhost:9428/insert/jsonline`

Query logs:
```bash
# Recent errors from api
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=service:api AND error' \
  --data-urlencode 'limit=20'

# Recent workflow failures from worker
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=service:worker AND "WORKFLOW_FAILED"' \
  --data-urlencode 'limit=20'
```

## API Snapshot (scripts/agent/api-snapshot.sh)

Captures a point-in-time snapshot of key API state:
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/api-snapshot.sh
```
Outputs:
- API health status
- Worker health status
- Worker Prometheus metrics summary
- Recent log tail from VictoriaLogs

## DB Snapshot (scripts/agent/db-snapshot.sh)

Not applicable for local dev (HBase is remote). The script verifies connectivity:
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/db-snapshot.sh
```
Outputs:
- HBase reachability check (via worker health endpoint — worker healthcheck indirectly covers HBase)
- Redis sentinel status

## Pipeline Verification (scripts/agent/verify-pipeline.sh)

End-to-end probe that verifies the entire stack is operational:
```bash
bash /Users/nidhi.b/IdeaProjects/drift/scripts/agent/verify-pipeline.sh
```
Steps:
1. Check API health
2. Check Worker health
3. Check VictoriaLogs reachability
4. Run a test query against VictoriaLogs for recent logs
5. Optionally: attempt a `GET /v3/workflow/probe-test` to verify Temporal connectivity

Exit 0 = pipeline healthy. Exit 1 = at least one component failed.
