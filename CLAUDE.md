# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

All commands run from the repo root. Always use `-am` (also-make) so upstream library modules are compiled first.

```bash
# Full build, skip tests
mvn clean package -DskipTests -pl java-sdk,commons,api,worker -am

# Full build with tests
mvn clean verify -pl java-sdk,commons,api,worker -am

# Single module (also builds its upstream deps)
mvn clean package -DskipTests -pl api -am
mvn clean package -DskipTests -pl worker -am

# Run tests for one module
mvn test -pl worker -am

# Run a specific test class
mvn test -pl worker -Dtest=WorkerArchTest -am

# Coverage report (output: <module>/target/site/jacoco/index.html)
mvn verify -pl api,worker,commons,java-sdk -am
```

If the build runs out of memory: `export MAVEN_OPTS="-Xmx2g"`

## Running Locally

Services run as native JVM processes (not in Docker). The observability stack (Vector + VictoriaLogs) runs in Docker Compose.

```bash
# Start observability stack (must be running before services)
docker compose up -d

# Boot both services
bash scripts/agent/boot.sh

# Or start individually after building:
# API — port 8000 (app), 8001 (admin)
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -Xms512m -Xmx1g -Djava.net.preferIPv4Stack=true \
     -jar api/target/api-*.jar \
     server api/src/main/resources/config/configuration.yaml

# Worker — port 7200 (app), 7201 (admin), 9090 (Prometheus)
java --add-opens java.base/java.lang=ALL-UNNAMED \
     -Dgroovy.use.classvalue=true \
     -Xms512m -Xmx1g -Djava.net.preferIPv4Stack=true \
     -cp worker/target/worker-*.jar \
     com.flipkart.drift.worker.bootstrap.WorkerApplication \
     server worker/src/main/resources/config/configuration.yaml
```

Rancher Desktop must be running with Temporal (:7233), HBase (ZooKeeper :2181, Thrift :9090), and Redis Sentinel (:26379) already started before booting either service.

Health checks: `curl http://localhost:8001/healthcheck` (API) and `curl http://localhost:7201/healthcheck` (worker).

## Architecture

Drift is a definition-driven workflow orchestration platform on top of Temporal. Workflows are DAGs stored as data (in HBase), not code — the worker reads them at runtime and executes them dynamically.

### Module Dependency Graph

```
java-sdk  (no internal deps)
   └── commons  (depends on: java-sdk)
         ├── api     (depends on: commons, java-sdk)
         └── worker  (depends on: commons, java-sdk)
```

`api` and `worker` are sibling services and must never import each other. This is enforced by ArchUnit tests in each module's `src/test/java/.../arch/` package.

### Module Roles

- **java-sdk** — Public API surface: request/response model classes, `WorkflowStatus` enum, and the three SPI extension points (`ABTestingProvider`, `TokenProvider`, `SchedulerProvider`). SPI implementations are loaded via `ServiceLoader`; the built-in no-ops live here. This is what consumers add as a Maven dependency. See `examples/worker-flipkart/` for a reference SPI implementation.

- **commons** — Internal shared layer. Owns: domain model (`NodeDefinition` hierarchy, `WorkflowNode`, `Workflow`), HBase entities and DAOs (`NodeHB`, `WorkflowHB`, `WorkflowContextHB`), Guava-backed caches (`NodeDefinitionCache`, `WorkflowCache`), the `GenericWorkflow` Temporal interface, and utilities. Guice wiring for the persistence layer lives in `DriftEntityModule`.

- **api** — Dropwizard HTTP service (:8000/:8001). Three JAX-RS resources (`WorkflowResource`, `NodeDefinitionResource`, `WorkflowDefinitionResource`). `TemporalService` submits workflow runs to Temporal. `RedisPubSubService` publishes cache-invalidation messages on write. Guice-wired via `WorkflowClientModule` + `DriftEntityModule`.

- **worker** — Dropwizard Temporal worker (:7200/:7201). `GenericWorkflowImpl` is the single Temporal workflow implementation. `WorkflowNodeExecutor` drives the node execution loop. Each node type maps to a Temporal activity (e.g., `HttpNodeNodeActivityImpl`, `BranchNodeNodeActivityImpl`). `TemporalWorkerManaged` registers all activities and starts the worker factory as a Dropwizard `Managed` component. `RedisCacheInvalidator` subscribes to Redis pub/sub to evict stale cache entries.

### How a Workflow Executes

1. `api` receives `POST /v3/workflow/start` and submits a `GenericWorkflow` run to Temporal with a `WorkflowStartRequest`.
2. Temporal dispatches to `GenericWorkflowImpl.startWorkflow()` on the worker.
3. `FetchWorkflowActivity` loads the `Workflow` DSL from cache (backed by HBase). Each `WorkflowNode` in the DSL only carries `resourceId`/`resourceVersion` references — `FetchWorkflowActivityImpl` iterates every node and calls `nodeDefinitionCache.get()` to populate the `nodeDefinition` field in-place before returning the enriched `Workflow`.
4. `WorkflowNodeExecutor.executeNode()` selects the activity options based on node type: `localActivityOptions` for in-process types (`INSTRUCTION`, `BRANCH`, `GROOVY`, `SUCCESS`, `FAILURE`); `activityOptionsV1` for external types (`HTTP`, `WAIT`, etc.).
5. Each node activity is dispatched by name convention: `NodeType.name().toLowerCase() + "Execute"` (e.g., `"httpExecute"`). For disconnected node execution the suffix is `"executeWithFatResponse"`. The activity fetches workflow context from HBase, executes its logic, and persists the updated context back to HBase.
6. `handleNodeResponseStatus()` routes the workflow to its next state based on `WorkflowStatus` returned by the activity. If the workflow reaches `WAITING` or `SCHEDULER_WAITING`, `GenericWorkflowImpl` blocks via `Workflow.await()` until a resume signal arrives.
7. When a workflow completes or goes async-complete, `Workflow.postWorkflowCompletionNodes` (a `List<String>` on the `Workflow` DSL) is executed non-blocking — failures there do not affect the completion status.

### Temporal Activity Options

All activity options are centralized in `worker/src/main/java/.../temporal/OptionsStore.java`. There are currently three sets:

| Constant | Used for | startToCloseTimeout | maxAttempts |
|---|---|---|---|
| `activityOptions` | Internal/infrastructure activities (`FetchWorkflow`, `WorkflowContextManager`, `ReturnControl`) | 30 minutes | 3 |
| `activityOptionsV1` | External node activities (HTTP, PROCESSOR, WAIT, disconnected nodes) | 10 seconds | 1 |
| `localActivityOptions` | Local node activities (BRANCH, GROOVY, INSTRUCTION, SUCCESS, FAILURE) | 10 seconds | 1 |

### API Start/Resume Synchrony — the Redis Pub/Sub Await Pattern

`api` starts a workflow via `WorkflowClient.start()` and then **blocks** waiting for the workflow to either complete or reach a `WAITING` state. This is implemented with a Redis pub/sub channel named `ASYNC_AWAIT_CHANNEL + workflowId`. The sequence:

1. `RedisPubSubService.subscribeAndExecute()` subscribes to the channel first, then — from within `onSubscribe` — calls `WorkflowClient.start()` (or the signal method for resume). This ordering prevents the race where the worker publishes before the subscriber is ready.
2. On the worker side, activities that reach a terminal state (COMPLETED, FAILED, WAITING, DELEGATED) call `ReturnControlActivity`, which publishes to the same channel.
3. `api` unblocks and returns the workflow state. The subscription has a 5-second timeout; if it expires the API returns `REQUEST_TIMEOUT`.

### HBase ZooKeeper Connection

Both `api` and `worker` read `zookeeper.quorum.hot` from Archaius `DynamicProperty` at the time the HBase `Connection` is first created (lazy singleton). The ZK quorum string is not set in the YAML — it comes from the Archaius remote property files (loaded from the S3/GCS paths given in `hbasePropertiesPath`/etc. YAML keys). Locally, set this property in the remote property file or override via `DynamicProperty` during testing.

### Cache and Invalidation

Both `api` and `worker` maintain independent Guava `LoadingCache` instances (`NodeDefinitionCache`, `WorkflowCache`) backed by HBase. Cache keys are composite: `entityId + version + tenant`. When `api` writes a node or workflow definition, it publishes an invalidation key to a Redis pub/sub channel. Both services subscribe and evict the affected key. Redis is never the source of truth — only used for invalidation signalling.

### Config Loading

Dropwizard `EnvironmentVariableSubstitutor` resolves `${ENV_VAR}` in the configuration YAML. The worker additionally loads Archaius `DynamicURLConfiguration` from five remote property files (HBase, lookup, auth, A/B, workflow) on startup, with a 20-second poll interval. These are used for the ZooKeeper quorum, A/B workflow mapping, and auth token generation at runtime.

### NodeDefinition Polymorphism

`NodeDefinition` is an abstract class with a `@JsonTypeInfo`/`@JsonSubTypes` setup keyed on the `"type"` field. Adding a new node type requires: a new subclass in `commons/model/node/`, a new entry in the `@JsonSubTypes` annotation, a new `NodeType` enum value, a new `@ActivityInterface` in `worker/activities/`, a new `*ActivityImpl` registered in `TemporalWorkerManaged.createWorkerFactory()`, and adding the type to `localActivityTypes` in `WorkflowNodeExecutor` if it should run as a local activity.

## Key Constraints

- **Temporal workflow code must be deterministic.** No `System.currentTimeMillis()`, no `Thread.sleep()`, no random in `worker/workflows/`. Use `io.temporal.workflow.Workflow.*` equivalents. This is enforced by `WorkerArchTest`.
- **HBase access only through DAO classes.** Direct `org.apache.hadoop.hbase.client.Table` usage outside `commons/persistence/dao/` is forbidden (ArchUnit enforced).
- **All config via environment variables.** No hardcoded hosts, ports, or credentials anywhere. Use `${ENV_VAR}` in YAML.
- **Layer boundaries are hard.** `java-sdk` imports nothing internal. `commons` imports only `java-sdk`. `api` and `worker` import `commons` and `java-sdk` but never each other.
- **Tests use JUnit 5.** Use `@ExtendWith(MockitoExtension.class)`, not `@RunWith(MockitoJUnitRunner.class)`.
