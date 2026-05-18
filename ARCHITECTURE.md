# ARCHITECTURE.md — Drift Workflow Platform

## System Overview

Drift is a **low-code, graph-based workflow execution platform** built on top of Temporal.io. Users define
workflows as directed graphs of typed nodes (HTTP, Groovy, Branch, Wait, etc.) via the Builder API, and the
Worker service executes them durably via Temporal activities.

```
                  ┌────────────────────────────────────────────┐
                  │               External Callers              │
                  │   (Flipkart services, manual triggers)      │
                  └───────────────┬────────────────────────────┘
                                  │ REST
                  ┌───────────────▼────────────────────────────┐
                  │            Drift API Service                │
                  │         Dropwizard :8000/:8001              │
                  │                                             │
                  │  WorkflowResource ──── TemporalService      │
                  │  NodeDefinitionResource ─ NodeDefService    │
                  │  WorkflowDefinitionResource ─ WfDefService  │
                  │                                             │
                  │  Persistence: HBase DAOs (via commons)      │
                  │  Cache: Redis Sentinel (via commons)        │
                  └───────────────┬────────────────────────────┘
                                  │ Temporal gRPC
                  ┌───────────────▼────────────────────────────┐
                  │           Temporal Frontend                  │
                  │      (external managed service)             │
                  └───────────────┬────────────────────────────┘
                                  │ Task Polling
                  ┌───────────────▼────────────────────────────┐
                  │           Drift Worker Service              │
                  │         Dropwizard :7200/:7201              │
                  │         Prometheus metrics :9090            │
                  │                                             │
                  │  GenericWorkflowImpl                        │
                  │    └─ WorkflowNodeExecutor                  │
                  │         └─ Activity stubs per NodeType      │
                  │                                             │
                  │  Activities:                                │
                  │    HttpNodeActivity (remote)                │
                  │    GroovyNodeActivity (local)               │
                  │    BranchNodeActivity (local)               │
                  │    InstructionNodeActivity (local)          │
                  │    ProcessorNodeActivity (remote)           │
                  │    WaitNodeActivity (remote)                │
                  │    SuccessNodeActivity (local)              │
                  │    FailureNodeActivity (local)              │
                  │    WorkflowContextManagerActivity (remote)  │
                  │    FetchWorkflowActivity (remote)           │
                  │    FetchNodeDefinitionActivity (remote)     │
                  │                                             │
                  │  External calls: HBase (context), Redis     │
                  │  SPI: TokenProvider, ABTestingProvider,     │
                  │       SchedulerProvider                     │
                  └────────────────────────────────────────────┘
```

---

## Module Dependency Graph

```
java-sdk  (public SDK — SPI interfaces + models)
    │
    └──► commons  (domain models, persistence DAOs, caches, Temporal GenericWorkflow interface)
              │
              ├──► api     (Dropwizard REST service — workflow execution + builder)
              │
              └──► worker  (Dropwizard Temporal worker — node activity execution)
```

**Rule: No upward dependencies.** `commons` must never import from `api` or `worker`. `java-sdk` must never
import from `commons`.

---

## Layer Architecture

### java-sdk (Public Contract Layer)
- **Purpose:** Everything a consumer (external workflow developer) needs to depend on.
- **Contents:** SPI interfaces (`TokenProvider`, `ABTestingProvider`, `SchedulerProvider`) with NoOp defaults; request/response models (`WorkflowStartRequest`, `WorkflowResponse`, etc.); client data models (`IssueDetail`, `Customer`, etc.).
- **Constraints:** Zero dependency on internal Drift modules. Must be stable — API changes here break external consumers.

### commons (Domain + Persistence Layer)
- **Purpose:** Shared domain models and persistence infrastructure used by both `api` and `worker`.
- **Contents:**
  - `model/node/` — `NodeDefinition` (abstract) and all subtypes (`HttpNode`, `GroovyNode`, `BranchNode`, etc.); `Workflow`, `WorkflowNode`.
  - `model/enums/` — `NodeType`, `WaitType`, `ExecutionMode`, `HttpMethod`, etc.
  - `model/temporal/` — `WorkflowState` (Temporal query state object).
  - `persistence/dao/` — `AbstractEntityDao`, `NodeDefinitionDao`, `WorkflowDefinitionDao`, `WorkflowContextHBDao` (all HBase-backed).
  - `persistence/cache/` — Redis-backed `NodeDefinitionCache` and `WorkflowCache` with version support.
  - `persistence/entity/` — HBase row entities (`NodeHB`, `WorkflowHB`, `WorkflowContextHB`).
  - `workflows/GenericWorkflow.java` — Temporal `@WorkflowInterface`.
- **Constraints:** Must not contain any REST resource or Temporal worker bootstrap code.

### api (REST Service Layer)
- **Purpose:** HTTP facade — accept external workflow requests, orchestrate calls to Temporal, provide builder CRUD.
- **Contents:**
  - `resources/` — JAX-RS resources: `WorkflowResource`, `NodeDefinitionResource`, `WorkflowDefinitionResource`.
  - `service/TemporalService.java` — communicates with Temporal frontend via `WorkflowStub` and `WorkflowClient`.
  - `service/builder/` — `NodeDefinitionService`, `WorkflowDefinitionService` — HBase read/write for definitions.
  - `service/RedisPubSubService.java` — Redis pub/sub for cache invalidation.
  - `bootstrap/DriftApplication.java` — Dropwizard application entry point.
  - `module/WorkflowClientModule.java` — Guice module wiring Temporal client + all services.
- **Constraints:** No direct HBase or Redis pool management (delegated to commons). No Temporal activity implementation. Must register all resources explicitly in `DriftApplication.run()`.

### worker (Temporal Worker Layer)
- **Purpose:** Poll Temporal task queues, execute workflow logic and node activities.
- **Contents:**
  - `workflows/GenericWorkflowImpl.java` — Temporal workflow implementation. Deterministic. No I/O.
  - `workflows/WorkflowNodeExecutor.java` — dispatches to the correct activity stub per `NodeType`.
  - `activities/` — all activity implementations (`HttpNodeNodeActivityImpl`, `GroovyNodeNodeActivity`, `InstructionNodeActivityImpl`, etc.).
  - `executor/` — `HttpExecutor` (Retrofit), `WaitTypeExecutor` subtypes.
  - `service/` — `WorkflowConfigStoreService`, `WorkflowContextHBService`, `IssueWorkflowMappingService`.
  - `translator/` — `GroovyTranslator`, `ClientComponentsParser`, `ClientResolvedDetailBuilder`.
  - `bootstrap/WorkerApplication.java` — Dropwizard application entry + Temporal worker registration.
  - `bootstrap/RedisCacheInvalidator.java` — subscribes to Redis pub/sub for cache invalidation.
- **Constraints:** Workflow code (`GenericWorkflowImpl`) must be deterministic — no system time, no direct I/O. Activities may do I/O. Use `Workflow.getLogger()` inside workflow code.

---

## Key Design Patterns

### Workflow Execution Flow
1. External caller POSTs to `POST /v3/workflow/start` with `WorkflowStartRequest` (includes `issueDetail`, `workflowId`, `threadContext`).
2. `TemporalService` starts a `GenericWorkflow` Temporal workflow execution.
3. `GenericWorkflowImpl.startWorkflow()` runs on the Worker:
   a. Fetches the `Workflow` DSL from HBase (via `FetchWorkflowActivity`).
   b. Iterates over `WorkflowNode` states from `startNode`.
   c. Delegates each node to `WorkflowNodeExecutor.executeNode()`.
   d. `WorkflowNodeExecutor` selects local or remote activity stub based on `NodeType`.
4. Activities execute the node logic (HTTP call, Groovy eval, branch, etc.) and return `ActivityThinResponse`.
5. Workflow continues to the next node based on the response or transitions to success/failure terminal.
6. Workflow state is tracked in `WorkflowState` (queried via `@QueryMethod`).

### Cache Invalidation
- Both API and Worker maintain a Redis-backed in-memory cache for `NodeDefinition` and `Workflow` objects.
- On a definition update (via API), `RedisPubSubService` publishes an invalidation message.
- `RedisCacheInvalidator` in Worker subscribes and evicts the cached entry.
- Cache TTL is also enforced via `staticCacheRefreshConfig` (background refresh every N minutes).

### SPI Extension Points
- **`TokenProvider`** — provides auth tokens for outbound HTTP calls. Default: `NoOpTokenProvider`. Override via `META-INF/services/com.flipkart.drift.sdk.spi.auth.TokenProvider`.
- **`ABTestingProvider`** — provides A/B test cohort resolution. Default: `NoOpABTestingProvider`.
- **`SchedulerProvider`** — integrates with a scheduling backend for `WAIT` nodes with `SCHEDULER` wait type.
- Extensions are loaded via `ServiceLoader` from the classpath — the worker's `entrypoint.sh` puts extension JARs first in the classpath to ensure their `META-INF/services` files take precedence.

### Temporal Activity Types
- **Local activities** (run in-process, no network, deterministic-safe): `INSTRUCTION`, `BRANCH`, `GROOVY`, `SUCCESS`, `FAILURE`.
- **Remote activities** (may do I/O, have retry policies, heartbeating): `HTTP`, `PROCESSOR`, `WAIT`, `CONTEXT_OVERRIDE`, `DELEGATE`, `CHILD`, workflow context management, fetch operations.

---

## Data Model

### NodeDefinition (HBase table: node definitions)
- `id`: business key (e.g., `send-email-v1`)
- `name`: human-readable name
- `type`: `NodeType` enum value
- `parameters`: list of context keys the node reads
- `version`: semantic version string
- Subtypes carry additional config: `HttpNode` has URL/method/headers/body; `GroovyNode` has script; `BranchNode` has conditions; etc.

### Workflow (HBase table: workflow definitions)
- `id`: business key
- `startNode`: name of the first `WorkflowNode` to execute
- `states`: `Map<String, WorkflowNode>` — named nodes in the workflow graph
- `defaultFailureNode`: fallback on unhandled errors
- `postWorkflowCompletionNodes`: nodes to run after terminal nodes

### WorkflowState (Temporal query object — in-memory only)
- `status`: `WorkflowStatus` enum
- `currentNodeRef`: name of the currently executing node
- `errorMessage`: populated on failure
- Returned by `GET /v3/workflow/{workflowId}` via Temporal `@QueryMethod`.

---

## Architecture Rules (enforced)

| Rule | Module | Tool |
|------|--------|------|
| No upward module imports | All | Maven dependency scope + arch enforcement |
| No Spring annotations | All | ArchUnit (to be configured) |
| No hardcoded URLs/credentials | All | Code review + env var substitution |
| Workflow code is deterministic | worker | Temporal SDK linting |
| All resources explicitly registered | api | Integration test coverage |
| HBase access only via AbstractEntityDao | commons/api/worker | ArchUnit |
| SPI loading via ServiceLoader | worker | Convention + test coverage |
