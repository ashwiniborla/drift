# RELIABILITY.md — Drift Reliability & Operations Guide

## Reliability Architecture

Drift achieves durability through Temporal — all workflow execution state is persisted by Temporal and survives
API and Worker restarts. The platform is designed for at-least-once execution of activities with configurable
retry policies.

## Failure Modes and Recovery

### API Service Failure
- **Impact:** New workflow start/resume requests are blocked. In-flight workflows continue executing on the Worker.
- **Recovery:** Restart API. Temporal state is intact. No manual intervention needed for running workflows.

### Worker Service Failure
- **Impact:** Workflow tasks queue up in Temporal. No progress on in-flight workflows.
- **Recovery:** Restart Worker. Temporal replays workflow history to restore state and resumes from the last activity boundary.

### HBase Unavailability
- **Impact:** Node definition and workflow definition reads fail. Cache serves recent data for TTL window.
- **Recovery:** HBase recovery is automatic. Redis cache (5-minute TTL by default) absorbs brief outages.
  - Worker cache: `staticCacheRefreshConfig.nodeDefinitionConfig: 5` (minutes)
  - Worker cache: `staticCacheRefreshConfig.workflowConfig: 5` (minutes)

### Redis Sentinel Failure
- **Impact:** Cache becomes stale; falls back to HBase for every read (latency impact). Pub/sub invalidation stops.
- **Recovery:** Redis sentinel recovery is automatic. Cache will re-warm on next HBase read.

### Temporal Frontend Failure
- **Impact:** All new workflow starts fail. Worker cannot poll for new tasks.
- **Recovery:** Temporal cluster recovery. Worker reconnects automatically via gRPC retry.

## Temporal Retry Policies

Activities with `@ActivityInterface` use the default Temporal retry policy unless overridden in `OptionsStore`:
- **Default:** 10 retry attempts, exponential backoff starting at 1s, max 100s.
- **Non-retryable failures:** `ApplicationFailure.newNonRetryableFailure(...)` — used for business logic errors
  (workflow not found, invalid node definition, nested child workflows).

## Metrics

The Worker exposes Prometheus metrics at `http://localhost:9090/metrics` (configurable via `prometheusConfig`).

Key metrics to monitor:
- `workflow_start_duration` — latency of `startWorkflow` (Dropwizard `@Timed`)
- `workflow_resume_duration` — latency of `resumeWorkflow`
- Temporal SDK built-in metrics: `temporal_workflow_task_schedule_to_start_latency`, `temporal_activity_schedule_to_start_latency`
- JVM: GC pause time, heap usage, thread count (via JMX, port 5603 on worker / 5503 on api)

## Thread Pool Sizing (Worker)

Configured via `workerDynamicOptions` in worker `configuration.yaml`:
```yaml
workerDynamicOptions:
  workflowTaskPoller: 20      # Temporal workflow task poll threads
  activityTaskPoller: 50      # Temporal activity task poll threads
  workflowCacheSize: 600      # Max cached sticky workflow threads
  maxWorkflowThreadCount: 800 # Max concurrent workflow coroutine threads
```

Adjust for your throughput. High `activityTaskPoller` is appropriate when most time is in I/O-bound activities (HTTP calls).

## JVM Heap Configuration

Both services read `JVM_XMS` and `JVM_XMX` from environment variables (set in entrypoint scripts).
Recommended for local dev:
```bash
export JVM_XMS=256m
export JVM_XMX=1g
```

GC logs are written to `/var/log/drift-api/gc.log` (api) and `/var/log/drift-worker/gc.log` (worker).
Heap dumps on OOM are saved to the same directories.

## Health Checks

| Service | Endpoint | Expected Response |
|---------|----------|------------------|
| API | `http://localhost:8001/healthcheck` | `{"deadlocks":{"healthy":true}}` |
| Worker | `http://localhost:7201/healthcheck` | `{"deadlocks":{"healthy":true}}` |

Dropwizard health checks include deadlock detection by default. Add custom health checks for Temporal
and HBase connectivity in production.

## Log-Based Alerting

VictoriaLogs query patterns for alert conditions:
```
# Non-retryable workflow failures
WORKFLOW_NOT_FOUND OR START_NODE_NOT_FOUND OR INVALID_CHILD_NODE OR NODE_DEFINITION_NULL

# Activity failures
Error while executing workflow

# HBase connection errors
HBase connection

# Redis errors
JedisConnectionException OR JedisSentinelPool
```

## Cache Invalidation Flow

1. Developer updates a `NodeDefinition` or `Workflow` via API
2. `NodeDefinitionService` / `WorkflowDefinitionService` writes to HBase
3. `RedisPubSubService` publishes invalidation message to Redis channel
4. `RedisCacheInvalidator` in Worker subscribes and calls `cache.invalidate(id)`
5. Next cache access triggers fresh HBase read
6. Background `staticCacheRefreshConfig` also refreshes all entries every N minutes

## Graceful Shutdown

Both services configure `awaitTerminationTimeoutInSec: 20` (worker default). Dropwizard will drain in-flight
requests before JVM shutdown. Temporal worker will stop polling but allow in-flight activities to complete
within the activity timeout.
