# RELIABILITY.md — Drift Reliability and Observability

How Drift handles failures, metrics, logging, and operational visibility.

---

## Logging

### Format

Both services output **structured logs** to stdout using the Dropwizard logback appender:

```
%date %level [%thread] %logger{0} [%X{id}] %msg%n
```

- `%X{id}` — correlation/request ID propagated via MDC (set by `RequestFilter`)
- Logs flow: app stdout → Vector → VictoriaLogs

### Log Levels

| Logger | Default Level |
|--------|--------------|
| root | INFO |
| `com.flipkart.drift` | INFO |

Change log level at runtime via Dropwizard admin:
```bash
curl -X POST http://localhost:8001/tasks/log-level \
  -d 'logger=com.flipkart.drift&level=DEBUG'
```

---

## Metrics

### API Service (:8001)

Dropwizard Metrics exposed at:
- `/metrics` (admin port :8001) — JSON format
- JMX (port 5503)
- `@Timed` and `@ExceptionMetered` annotations on resource methods generate per-endpoint metrics

Key metrics:
- `com.flipkart.drift.api.resources.WorkflowResource.startWorkflow` — p50/p95/p99 latency
- `com.flipkart.drift.api.resources.WorkflowResource.startWorkflow.exceptions` — error rate

### Worker Service (:7201 + :9090)

- Dropwizard Metrics at `/metrics` (admin :7201)
- Prometheus scrape endpoint at `:9090/metrics`
- JMX at port 5603

Key metrics:
- `workflow_execution_total` — total workflows executed
- `activity_execution_duration` — per-node execution time
- Temporal SDK built-in metrics (namespace, task_queue, workflow_type tags)

---

## Health Checks

### API (:8001/healthcheck)
```json
{
  "deadlocks": {"healthy": true},
  "temporal-connection": {"healthy": true},
  "redis-sentinel": {"healthy": true}
}
```

### Worker (:7201/healthcheck)
```json
{
  "deadlocks": {"healthy": true},
  "temporal-worker": {"healthy": true},
  "redis-sentinel": {"healthy": true}
}
```

---

## Failure Modes and Mitigation

### Temporal Unavailable

| Component | Behaviour |
|-----------|-----------|
| API | `startWorkflow` returns 503. Temporal client retries with exponential backoff (SDK default). |
| Worker | Worker stops polling. Resumes automatically when Temporal reconnects. In-flight workflows are retried by Temporal (durable execution guarantee). |

### HBase Unavailable

| Component | Behaviour |
|-----------|-----------|
| API | NodeDefinition/WorkflowDefinition reads fail with 500. Served from in-memory cache if cache is warm (up to TTL). Write operations fail. |
| Worker | Node execution reads from in-memory cache. Cache miss → workflow activity failure → Temporal retries per retry policy. |

**Mitigation**: Cache TTL (5 minutes for NodeDefinition and WorkflowDefinition, per `staticCacheRefreshConfig`). Ensure retry policy on Temporal activities is set to retry on transient HBase failures.

### Redis Unavailable

| Component | Behaviour |
|-----------|-----------|
| API | Redis Sentinel reconnect attempts (Jedis built-in). Config cache invalidation pub/sub messages are dropped during downtime — staleness window equals Redis downtime. |
| Worker | Same as API. |

**Mitigation**: Redis Sentinel provides HA. Set `testOnBorrow: true` (configured) to detect stale connections. Cache invalidation is eventually consistent by design.

### OOM / JVM Crash

HeapDump is captured automatically:
- API: `/var/log/drift-api/heap_dump.hprof`
- Worker: `/var/log/drift-worker/heap_dump.hprof`

GC logs:
- API: `/var/log/drift-api/gc.log`
- Worker: `/var/log/drift-worker/gc.log`

---

## SLOs (Target)

| Metric | Target |
|--------|--------|
| API p99 latency (start workflow) | < 500ms |
| API availability | 99.9% |
| Worker workflow execution success rate | > 99% |
| Cache hit rate (NodeDefinition) | > 95% |

---

## Observability Stack (Local)

```
┌─────────────┐      ┌──────────┐      ┌──────────────────┐
│  API stdout  │─────►│  Vector  │─────►│  VictoriaLogs    │
│  Worker     │      │ (sidecar)│      │  :9428           │
│  stdout     │      └──────────┘      │                  │
└─────────────┘                        │  Query UI:       │
                                       │  /select/...     │
                                       └──────────────────┘
```

Query examples:
```bash
# Errors in last 10 minutes
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=level:ERROR' \
  --data-urlencode 'start=10m'

# Slow requests (contains "took" > threshold)
curl -G 'http://localhost:9428/select/logsql/query' \
  --data-urlencode 'query=service:drift-api AND "slow"' \
  --data-urlencode 'start=5m'
```
