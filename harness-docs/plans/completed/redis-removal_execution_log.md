# Execution Log: redis-removal

**Feature tag:** `redis-removal`
**Plan doc:** `harness-docs/plans/active/redis-removal_execution_plan.md`

---

## Execution Summary

| Subtask | Layer | Module | Commit SHA | Status | Completed At |
|---------|-------|--------|------------|--------|--------------|
| subtask-1: java-sdk-models | 0 | java-sdk | e978b10 | STATIC_PASS / VALIDATED | 2026-05-24T00:00:00Z |
| subtask-2: commons-changes | 0 | commons | e978b10 | STATIC_PASS / VALIDATED | 2026-05-24T00:00:00Z |
| subtask-3a: api-temporal-async | 1 | api | 505ee57 | STATIC_PASS / VALIDATED | 2026-05-24T00:00:00Z |
| subtask-3b: api-cleanup-services | 1 | api | cb8e546 | STATIC_PASS / VALIDATED | 2026-05-24T00:00:00Z |
| subtask-4: worker-callback-core | 1 | worker | 9bbb091 | STATIC_PASS / VALIDATED | 2026-05-24T00:00:00Z |
| subtask-5a: worker-bootstrap-cleanup | 2 | worker | 50cbb7d | STATIC_PASS / VALIDATED | 2026-05-24T00:00:00Z |
| subtask-5b: worker-workflow-changes | 3 | worker | d29793d | STATIC_PASS / VALIDATED | 2026-05-24T15:10:00Z |
| subtask-6: noncode-cleanup | 4 | root | cf6a4ce | STATIC_PASS / VALIDATED | 2026-05-24T15:20:00Z |

## Validate Summary

- **validate.md verdict:** LGTM
- **Validated at:** 2026-05-24T15:45:00Z
- **Runtime verification:** Docker stack healthy (VictoriaLogs+Vector). API rebuilt with Java 17, booted successfully without Redis.
- **Endpoints verified:** POST /v3/workflow/start returns 202 CREATED, PUT /v3/workflow/resume returns 202 RUNNING
- **Error cases verified:** ftp:// callbackUrl returns 400, null callbackUrl accepted
- **Observability verified:** feature=redis-removal logs in VictoriaLogs (8 entries, 0 errors)
- **Redis removal verified:** No jedis/Jedis in .java or pom.xml. No redisConfiguration in YAML.
- **Metrics verified:** 4/4 criteria pass

---

## Cleanup Summary

- **Cleanup agent ran:** 2026-05-24T17:25:00Z
- **Files modified:**
  - `api/src/main/java/com/flipkart/drift/api/service/TemporalService.java`
  - `api/src/main/java/com/flipkart/drift/api/client/WorkerCacheInvalidationClient.java`
  - `api/src/main/java/com/flipkart/drift/api/service/builder/WorkflowDefinitionService.java`
  - `api/src/main/java/com/flipkart/drift/api/service/builder/NodeDefinitionService.java`
  - `worker/src/main/java/com/flipkart/drift/worker/activities/CallbackActivityImpl.java`
  - `worker/src/main/java/com/flipkart/drift/worker/workflows/WorkflowNodeExecutor.java`
  - `worker/src/main/java/com/flipkart/drift/worker/task/CacheInvalidationTask.java`
- **Total debug artifacts processed:** 38 (across 7 files)
  - PROBE:: marker comments stripped: 17
  - `_probeStartMs` timing variable stripped: 1
  - Timing-only log (durationMs with `_probeStartMs`) stripped: 1
  - Debug skip log stripped (internal private method): 1
  - Javadoc feature tag comment stripped: 1
  - Feature-tagged logs stripped (subtotal): 0 (all feature-tagged logs were retained)
- **Logs retained and converted to permanent:** 17 (feature tag removed, operational content kept)
  - TemporalService: 4 logs retained (entry + error for executeWorkflow, entry + error for resumeWorkflow)
  - WorkerCacheInvalidationClient: 5 logs retained (disabled skip, DNS failure, per-pod OK, per-pod bad status, per-pod exception)
  - CallbackActivityImpl: 6 logs retained (entry, 2xx success, 4xx NON_RETRYABLE, 5xx RETRYABLE, exception RETRYABLE)
  - CacheInvalidationTask: 2 logs retained (success result + error catch)
- **Tests after stripping:** PASS — api: 11/11, worker: 22/22
- **Full build after stripping:** PASS — `mvn clean package -DskipTests` with JAVA_HOME=temurin-17 exits 0 (BUILD SUCCESS, all 5 modules)
