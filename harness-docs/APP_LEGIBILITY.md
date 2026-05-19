# APP_LEGIBILITY.md — Drift Service Instrumentation Reference

How to boot, query, and observe the Drift services via harness agent scripts.

---

## Agent Scripts

All scripts live in `scripts/agent/` and are executable.

| Script | Purpose |
|--------|---------|
| `check-prereq.sh` | Verify Docker daemon is running before any infra operation |
| `boot.sh` | Build + start both services (API and Worker) |
| `health.sh` | Run health checks against all services |
| `query-logs.sh` | Query VictoriaLogs for service logs |
| `api-snapshot.sh` | Capture a snapshot of the API's current state (health, metrics) |
| `db-snapshot.sh` | Not applicable — HBase and Redis are external (Rancher Desktop) |
| `verify-pipeline.sh` | Run end-to-end smoke test against a running stack |

---

## boot.sh Behaviour

1. Calls `check-prereq.sh` — hard-stops if Docker is unavailable.
2. Checks required environment variables are set.
3. Runs `mvn clean package -DskipTests -pl java-sdk,commons,api,worker -am` if JARs are stale.
4. Starts Docker Compose (observability stack).
5. Launches API service (waits for :8001/healthcheck to return HTTP 200).
6. Launches Worker service (waits for :7201/healthcheck to return HTTP 200).
7. Prints connection summary.

---

## Connectivity Summary

After `boot.sh` completes successfully:

```
API service:
  Application: http://localhost:8000
  Admin:       http://localhost:8001
  Health:      http://localhost:8001/healthcheck
  Metrics:     http://localhost:8001/metrics

Worker service:
  Application: http://localhost:7200
  Admin:       http://localhost:7201
  Health:      http://localhost:7201/healthcheck
  Prometheus:  http://localhost:9090/metrics

Observability:
  VictoriaLogs: http://localhost:9428
  Query UI:     http://localhost:9428/select/logsql/query

External (Rancher Desktop):
  Temporal UI:  http://localhost:8080
  Temporal gRPC: localhost:7233
```

---

## Smoke Test Endpoints

These endpoints serve as quick sanity checks after boot:

```bash
# API ping
curl -s http://localhost:8001/ping

# Worker ping
curl -s http://localhost:7201/ping

# API health (all components)
curl -s http://localhost:8001/healthcheck | python3 -m json.tool

# Worker health
curl -s http://localhost:7201/healthcheck | python3 -m json.tool

# Check a known workflow definition (requires a pre-registered definition)
curl -s http://localhost:8000/workflowDefinition/<id> | python3 -m json.tool
```

---

## VictoriaLogs Query Reference

VictoriaLogs uses LogsQL syntax:

```bash
BASE_URL="http://localhost:9428/select/logsql/query"

# All logs for api in last 5 minutes
curl -G "$BASE_URL" \
  --data-urlencode 'query=service:drift-api' \
  --data-urlencode 'start=5m'

# Error and above, all services, last 10 min
curl -G "$BASE_URL" \
  --data-urlencode 'query=level:ERROR OR level:WARN' \
  --data-urlencode 'start=10m'

# Specific workflow ID trace
curl -G "$BASE_URL" \
  --data-urlencode 'query=<workflowId>' \
  --data-urlencode 'start=1h'

# Worker node execution logs
curl -G "$BASE_URL" \
  --data-urlencode 'query=service:drift-worker AND WorkflowNodeExecutor' \
  --data-urlencode 'start=5m'
```

---

## Metrics Reference

### API Dropwizard Metrics (JSON at :8001/metrics)

Key metric paths:
```
timers:
  com.flipkart.drift.api.resources.WorkflowResource.startWorkflow
  com.flipkart.drift.api.resources.WorkflowResource.resumeWorkflow
  com.flipkart.drift.api.resources.WorkflowResource.terminateWorkflow
  com.flipkart.drift.api.resources.NodeDefinitionResource.addNode
  com.flipkart.drift.api.resources.WorkflowDefinitionResource.*

meters:
  com.flipkart.drift.api.resources.WorkflowResource.startWorkflow.exceptions
```

### Worker Prometheus Metrics (:9090/metrics)

```
# Temporal SDK metrics
temporal_workflow_completed_total
temporal_workflow_failed_total
temporal_activity_execution_failed_total
temporal_activity_execution_latency_bucket

# JVM metrics (Micrometer)
jvm_memory_used_bytes
jvm_gc_pause_seconds
process_cpu_usage
```
