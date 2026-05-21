# Expanded PRD: Retry and Timeout Configuration for Drift Workflows

**Feature tag:** retry-timeout-config
**Branch:** feature/retry-timeout-config
**Author:** Ashwini Borla
**Date:** 2026-05-19
**Status:** DRAFT — awaiting Confluence LGTM

---

## 1. Background and Problem Statement

Drift is a workflow orchestration platform built on top of Temporal. Each workflow is a directed graph of `WorkflowNode` entries (the DSL), where every node is a typed activity (`HTTP`, `GROOVY`, `BRANCH`, `INSTRUCTION`, `PROCESSOR`, `SUCCESS`, `FAILURE`, `WAIT`, `DELEGATE`, `CHILD`, `CONTEXT_OVERRIDE`).

Activity execution options are currently centralised in `OptionsStore.java` (worker module). As of today that class defines three static constants that cover all external-node activities:

| Constant | Timeout | Max Attempts | Initial Interval | Max Interval | Backoff |
|---|---|---|---|---|---|
| `activityOptions` | 30 minutes | 3 | 1 s | 20 s | 2.0 |
| `activityOptionsV1` | 10 seconds | 1 | — | — | — |
| `localActivityOptions` | 10 seconds | 1 | — | — | — |

`WorkflowNodeExecutor.executeNode()` unconditionally uses `activityOptionsV1` for all external (non-local) node activities. Internal/platform activities (`ReturnControlActivity`, `WorkflowContextManagerActivity`, `FetchWorkflowActivity`) use `activityOptions` (30-minute timeout, 3 retries).

**Consequences of the current design:**

1. All external node activities get a hard 10-second timeout with zero retries regardless of what the node actually does (a slow HTTP call to an external vendor can legitimately need 30 seconds; an idempotent HTTP call to an internal service should retry on transient failures).
2. Workflow authors cannot tune per-node retry/timeout behaviour without modifying platform code and redeploying the worker.
3. The platform has no concept of "default for external nodes" that can be changed via configuration without code changes.

---

## 2. Goals

1. Allow workflow authors to specify per-node retry and timeout configuration in the `WorkflowNode` DSL stored in HBase.
2. Provide operator-tunable platform-level defaults for external-node activities, configurable via Dropwizard YAML without code changes.
3. Ensure internal/platform activities (`ReturnControlActivity`, `WorkflowContextManagerActivity`, `FetchWorkflowActivity`) remain completely unaffected.

---

## 3. Non-Goals

- No workflow-wide (i.e., Temporal workflow-level) retry or timeout policies.
- No per-`NodeDefinition` type overrides (e.g., "all HTTP nodes default to 30 s"). Overrides are per `WorkflowNode` instance only.
- No UI changes to the Drift management console (API changes for the `WorkflowNode` schema are sufficient).
- No migration of existing workflows in HBase — absent fields fall back to platform defaults transparently.

---

## 4. Functional Requirements

### FR-1: `retryConfig` on `WorkflowNode` (commons module)

Add an optional `retryConfig` field to `WorkflowNode`:

```
retryConfig:
  maxAttempts: <int>            # required when retryConfig is present; 1 = no retry
  initialIntervalSeconds: <int> # optional; default 1
  maxIntervalSeconds: <int>     # optional; default 20
  backoffCoefficient: <double>  # optional; default 2.0
```

- `maxAttempts` is the only required sub-field when `retryConfig` is present.
- Remaining sub-fields default as shown. They must not need to be set explicitly when `maxAttempts` alone is sufficient.
- If `retryConfig` is entirely absent from the node, fall back to the platform default (`drift.worker.activity.default-max-attempts`).

### FR-2: `timeoutSeconds` on `WorkflowNode` (commons module)

Add an optional `timeoutSeconds` (`int`) field to `WorkflowNode`.

- When present, use this value as the `startToCloseTimeout` for the activity.
- When absent, fall back to the platform default (`drift.worker.activity.default-timeout-seconds`).

### FR-3: Platform-level defaults (worker module config)

Add two new optional fields to the worker configuration YAML, under a dedicated sub-object `activityDefaults` inside `DriftWorkerConfiguration`:

```yaml
activityDefaults:
  defaultMaxAttempts: 1         # maps to drift.worker.activity.default-max-attempts
  defaultTimeoutSeconds: 10     # maps to drift.worker.activity.default-timeout-seconds
```

- These fields are optional in the YAML; the JVM defaults are 1 attempt and 10 seconds (preserving existing behaviour for deployments that do not set them).
- They **only** affect external-node activity stubs created in `WorkflowNodeExecutor.executeNode()`. They must not change the `ActivityOptions` used for internal activities (`ReturnControlActivity`, `WorkflowContextManagerActivity`, `FetchWorkflowActivity`).

### FR-4: Activity options resolution in `WorkflowNodeExecutor` (worker module)

The activity stub for an external node must be built by merging node-level config with platform defaults using this priority order (highest wins):

```
node.retryConfig        (per-node, DSL)
node.timeoutSeconds     (per-node, DSL)
  |
  v  falls back to
activityDefaults.defaultMaxAttempts    (platform default, YAML)
activityDefaults.defaultTimeoutSeconds (platform default, YAML)
```

The resolution logic must be encapsulated in a new class `ActivityOptionsBuilder` (or similar) in the worker module so it is independently testable.

### FR-5: Scope boundary — internal activities are untouched

The following activity invocations in `WorkflowNodeExecutor` and `GenericWorkflowImpl` must continue to use `OptionsStore.activityOptions` (30-minute timeout, 3 retries) unchanged:

| Call site | Activity | Options used today |
|---|---|---|
| `GenericWorkflowImpl.initializeWorkflow` | `WorkflowContextManagerActivity.persistWorkflowState` | `activityOptions` |
| `GenericWorkflowImpl.resumeWorkflow` | `WorkflowContextManagerActivity.resumeWorkflowState` | `activityOptions` |
| `GenericWorkflowImpl.fetchDsl` | `FetchWorkflowActivity.fetchWorkflowBasedOnRequest` | `activityOptions` |
| `GenericWorkflowImpl.executeDisconnectedNode` | `FetchWorkflowActivity.fetchWorkflowNode` | `activityOptions` |
| `WorkflowNodeExecutor.handleWaitingState` | `ReturnControlActivity.exec` | `activityOptions` |
| `WorkflowNodeExecutor.handleFailedState` | `ReturnControlActivity.exec` | `activityOptions` |
| `WorkflowNodeExecutor.handleCompletedState` | `ReturnControlActivity.exec` | `activityOptions` |
| `WorkflowNodeExecutor.handleDelegatedState` | `ReturnControlActivity.exec` | `activityOptions` |
| `WorkflowNodeExecutor.executeWorkflowNode` (disconnected) | `WorkflowContextManagerActivity.disconnectedNodeState` | `activityOptions` |

`localActivityOptions` (used for `INSTRUCTION`, `BRANCH`, `GROOVY`, `SUCCESS`, `FAILURE` node types) is also **out of scope** — local activities always use the fixed `localActivityOptions` from `OptionsStore`.

---

## 5. Non-Functional Requirements

- **Backward compatibility:** All existing workflow DSLs stored in HBase that lack `retryConfig` and `timeoutSeconds` must continue to execute identically — the effective behaviour must be equivalent to the current `activityOptionsV1` (10 s, 1 attempt).
- **Performance:** The resolution logic runs inside a Temporal workflow execution context (deterministic, no I/O). It must be a pure in-memory calculation with negligible overhead.
- **Testability:** `ActivityOptionsBuilder` must be a plain Java class with no Temporal or Spring/Guice annotations, enabling unit tests without a Temporal test server.
- **Observability:** No new metrics or log lines are required for this feature. Temporal already logs activity timeouts and retry attempts at the SDK level.
- **Deployment:** No HBase schema migration. No Temporal workflow migration. YAML changes are optional (existing deployments continue with defaults).

---

## 6. Detailed Design Constraints (for HLD)

These constraints are derived from codebase analysis and must be respected by the HLD:

1. **`WorkflowNode` is in `commons` module.** The new `retryConfig` field and its POJO (`NodeRetryConfig`) must also live in `commons`. The `worker` module depends on `commons`; `commons` must not depend on `worker`. Adding any Temporal SDK types to `NodeRetryConfig` would create a circular or invalid dependency.

2. **`OptionsStore` is a static constants class.** It is not injected anywhere. The new `ActivityOptionsBuilder` class must be instantiated and injected via the Guice `WorkerModule`, not as a static method on `OptionsStore`.

3. **`WorkflowNodeExecutor` is constructed directly** (`new WorkflowNodeExecutor(workflowState)`) inside `GenericWorkflowImpl`'s no-arg constructor. Temporal requires workflow implementations to have a no-arg constructor. `WorkflowNodeExecutor` cannot receive Guice-injected dependencies via its constructor without restructuring. The `ActivityOptionsBuilder` dependency must therefore be provided as a static singleton or passed through `WorkflowNodeExecutor`'s constructor where `GenericWorkflowImpl` holds an injected reference — this is a design decision for the HLD to resolve.

4. **`DriftWorkerConfiguration` is the single Dropwizard config class.** New YAML fields must be added as a nested POJO (e.g., `ActivityDefaultsConfig`) annotated with `@JsonIgnoreProperties(ignoreUnknown = true)` and added as an optional field to `DriftWorkerConfiguration`. The nested POJO must supply default values so the field is truly optional in YAML.

5. **`WorkflowNodeType` vs `NodeType`:** The DSL uses `WorkflowNodeType` (on `WorkflowNode`) while internal routing in `WorkflowNodeExecutor` uses `NodeType` (from `NodeDefinition`). Both enums exist in `commons`. The external-node / local-activity distinction is decided by `NodeType` (from `NodeDefinition`), not `WorkflowNodeType`. The `ActivityOptionsBuilder` receives the resolved `NodeType` to decide whether to apply per-node options or fall through.

6. **Jackson deserialization:** `WorkflowNode` is deserialized from JSON stored in HBase via Jackson. The new `retryConfig` field is a nested POJO. Jackson's `@JsonIgnoreProperties(ignoreUnknown = true)` is already present on `WorkflowNode`, so old DSL JSON (without `retryConfig`) deserializes to `null` for the field — no migration needed.

---

## 7. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | A `WorkflowNode` with `retryConfig.maxAttempts=3` and `timeoutSeconds=45` results in a Temporal activity stub with `startToCloseTimeout=45s`, `maxAttempts=3`, `initialInterval=1s`, `maxInterval=20s`, `backoffCoefficient=2.0`. |
| AC-2 | A `WorkflowNode` with no `retryConfig` and no `timeoutSeconds` uses the platform defaults (configurable via YAML; default JVM values: 1 attempt, 10 s). |
| AC-3 | Setting `activityDefaults.defaultTimeoutSeconds=30` in `configuration.yaml` changes the effective timeout for all external-node activities that do not have a node-level `timeoutSeconds`. |
| AC-4 | `ReturnControlActivity`, `WorkflowContextManagerActivity`, and `FetchWorkflowActivity` continue to use 30-minute timeout and 3-retry options unchanged regardless of any per-node or platform-default configuration. |
| AC-5 | `INSTRUCTION`, `BRANCH`, `GROOVY`, `SUCCESS`, and `FAILURE` node types continue to use `localActivityOptions` (10 s, 1 attempt) unchanged. |
| AC-6 | All existing unit tests pass without modification (no behaviour change for absent `retryConfig`/`timeoutSeconds`). |
| AC-7 | New unit tests for `ActivityOptionsBuilder` cover: (a) node-level override wins, (b) fall-back to platform default, (c) partial override (timeout set, retry absent), (d) default platform values when YAML omits `activityDefaults`. |

---

## 8. Out-of-Scope Items (explicitly deferred)

- Retry configuration for `localActivityOptions` (INSTRUCTION, BRANCH, GROOVY node types).
- Workflow-level retry/timeout policies (Temporal `WorkflowOptions`).
- Dynamic config refresh (Archaius / dynamic property) for platform defaults — YAML-reload-on-restart is sufficient.
- `retryConfig` on `NodeDefinition` subtypes (e.g., `HttpNode`) — this feature targets the `WorkflowNode` wrapper, not the node type definition.

---

## 9. Open Questions (resolved)

| # | Question | Resolution |
|---|---|---|
| OQ-1 | Should `retryConfig` on `WorkflowNode` or `NodeDefinition`? | `WorkflowNode` — it is the instance-level wrapper; `NodeDefinition` is the type-level template. Per-instance config belongs at `WorkflowNode`. |
| OQ-2 | Should internal activities be affected by platform defaults? | No. Internal activities (`ReturnControl`, `WorkflowContextManager`, `FetchWorkflow`) have fixed long-timeout/multi-retry needs independent of workflow author intent. |
| OQ-3 | How does `ActivityOptionsBuilder` reach `WorkflowNodeExecutor` given Temporal's no-arg constructor requirement? | Deferred to HLD. Candidate patterns: (a) thread-local injection via `WorkflowImplementationOptions.setActivityOptions`, (b) static singleton holder initialised at bootstrap, (c) Temporal's `WorkflowImplementationOptions` per-activity-type options map. |
