# High-Level Design: Retry and Timeout Configuration for Drift Workflows

**Feature tag:** retry-timeout-config
**Branch:** feature/retry-timeout-config
**Author:** Ashwini Borla
**Date:** 2026-05-19
**Status:** DRAFT — awaiting Confluence LGTM
**PRD:** retry-timeout-config-prd-expanded.md

---

## 1. Overview

This document describes the high-level design for making per-node retry and timeout configuration first-class citizens in the Drift workflow DSL. Today every external-node activity executes with a hard-coded 10-second timeout and zero retries (`activityOptionsV1`). This design introduces three new constructs that together enable workflow authors to configure per-node behaviour without touching platform code:

1. **`NodeRetryConfig`** — a new POJO in `commons` that carries per-node retry parameters sourced from the HBase-stored DSL.
2. **`ActivityDefaultsConfig`** — a new nested POJO in `worker` config that exposes operator-tunable platform-level defaults via Dropwizard YAML.
3. **`ActivityOptionsBuilder`** — a new plain Java class in `worker` that merges the two sources and constructs the `ActivityOptions` used by `WorkflowNodeExecutor`.

The design preserves full backward compatibility: any existing workflow DSL without the new fields falls back to the same behaviour as `activityOptionsV1` (10 s, 1 attempt).

---

## 2. Current Architecture

```
┌──────────────────────────────────────────────────────────┐
│  commons module                                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │  WorkflowNode                                       │  │
│  │    instanceName, resourceId, type, parameters,      │  │
│  │    contextOverrideKey, nextNode, end,               │  │
│  │    nodeDefinition (set at fetch time)               │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│  worker module                                            │
│                                                           │
│  OptionsStore (static constants)                          │
│    activityOptions       → 30 min, 3 retries             │
│    activityOptionsV1     → 10 s,   1 attempt             │
│    localActivityOptions  → 10 s,   1 attempt (local)     │
│                                                           │
│  WorkflowNodeExecutor.executeNode()                       │
│    isLocal? → localActivityOptions                        │
│    external? → activityOptionsV1  ←── ALWAYS, no config  │
│                                                           │
│  Internal activities (ReturnControl, WCM, FetchWorkflow)  │
│    → activityOptions  (unchanged, hardcoded)             │
└──────────────────────────────────────────────────────────┘
```

**Problem:** The `activityOptionsV1` branch has no hook to pull in per-node DSL config or operator-supplied YAML defaults. The `WorkflowNodeExecutor` is constructed inside `GenericWorkflowImpl`'s no-arg constructor, which means it cannot receive Guice-injected dependencies through the normal constructor injection path.

---

## 3. Proposed Architecture

### 3.1 Component Map

```
┌──────────────────────────────────────────────────────────────────────┐
│  commons module                                                        │
│                                                                        │
│  WorkflowNode  (MODIFIED)                                             │
│    + retryConfig:    NodeRetryConfig   (nullable, from HBase DSL)    │
│    + timeoutSeconds: Integer           (nullable, from HBase DSL)    │
│                                                                        │
│  NodeRetryConfig  (NEW)                                               │
│    maxAttempts:          int   (required when present)               │
│    initialIntervalSeconds: int (default 1)                           │
│    maxIntervalSeconds:   int   (default 20)                          │
│    backoffCoefficient:   double (default 2.0)                        │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│  worker module                                                         │
│                                                                        │
│  ActivityDefaultsConfig  (NEW — nested in DriftWorkerConfiguration)  │
│    defaultMaxAttempts:      int  (default 1)                         │
│    defaultTimeoutSeconds:   int  (default 10)                        │
│                                                                        │
│  DriftWorkerConfiguration  (MODIFIED)                                │
│    + activityDefaults: ActivityDefaultsConfig   (optional field)     │
│                                                                        │
│  ActivityOptionsBuilder  (NEW — plain Java class, Guice singleton)   │
│    build(WorkflowNode, ActivityDefaultsConfig) → ActivityOptions     │
│    Resolution: node-level > platform-default                         │
│                                                                        │
│  WorkflowNodeExecutor  (MODIFIED)                                    │
│    executeNode():                                                     │
│      external? → ActivityOptionsBuilder.build(node, defaults)        │
│      local?    → OptionsStore.localActivityOptions  (unchanged)      │
│                                                                        │
│  Internal activities  (UNCHANGED)                                    │
│    → OptionsStore.activityOptions                                    │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 Resolution Logic

```
ActivityOptionsBuilder.build(WorkflowNode node, ActivityDefaultsConfig defaults)

  timeout = node.timeoutSeconds           != null  ?  node.timeoutSeconds
          : defaults.defaultTimeoutSeconds                         (10 if YAML absent)

  maxAttempts = node.retryConfig          != null  ?  node.retryConfig.maxAttempts
              : defaults.defaultMaxAttempts                        (1 if YAML absent)

  initialInterval = (node.retryConfig != null && node.retryConfig.initialIntervalSeconds set)
                      ? node.retryConfig.initialIntervalSeconds
                      : 1   (fixed default)

  maxInterval     = (node.retryConfig != null && node.retryConfig.maxIntervalSeconds set)
                      ? node.retryConfig.maxIntervalSeconds
                      : 20  (fixed default)

  backoff         = (node.retryConfig != null && node.retryConfig.backoffCoefficient set)
                      ? node.retryConfig.backoffCoefficient
                      : 2.0 (fixed default)

  return ActivityOptions.newBuilder()
           .setStartToCloseTimeout(Duration.ofSeconds(timeout))
           .setRetryOptions(RetryOptions.newBuilder()
               .setMaximumAttempts(maxAttempts)
               .setInitialInterval(Duration.ofSeconds(initialInterval))
               .setMaximumInterval(Duration.ofSeconds(maxInterval))
               .setBackoffCoefficient(backoff)
               .build())
           .build()
```

### 3.3 Solving the Temporal No-Arg Constructor Constraint

Temporal requires workflow implementations to have a no-arg constructor (`GenericWorkflowImpl()`). The no-arg constructor directly calls `new WorkflowNodeExecutor(workflowState)`, so `WorkflowNodeExecutor` cannot receive Guice-injected dependencies through constructor injection via the normal Guice path.

**Chosen solution: static singleton holder, initialised at bootstrap.**

`ActivityOptionsBuilder` will be stored in a `ActivityOptionsBuilderHolder` class — a simple static holder pattern. `TemporalWorkerManaged` (which is a Guice singleton and has full access to the `DriftWorkerConfiguration`) initialises the holder once during `start()`, before the worker starts polling. `WorkflowNodeExecutor` reads from the holder.

```
Bootstrap sequence:
  WorkerApplication.run()
    → Guice injector creates TemporalWorkerManaged
    → TemporalWorkerManaged.start() called by Dropwizard
        → ActivityOptionsBuilderHolder.init(
               new ActivityOptionsBuilder(config.getActivityDefaults()))
        → workerFactory.start()

Runtime sequence:
  Temporal dispatches workflow task
    → GenericWorkflowImpl() no-arg constructor called
    → new WorkflowNodeExecutor(workflowState)
    → executeNode():
        → ActivityOptionsBuilderHolder.get().build(node, defaults)
```

This avoids ThreadLocal complexity and avoids changing the `GenericWorkflowImpl` constructor signature. The holder is set once before the worker starts and is thereafter read-only, making it safe in Temporal's deterministic replay environment.

**Alternative considered and rejected: per-activity-type `WorkflowImplementationOptions`**
Temporal supports overriding `ActivityOptions` per activity type via `WorkflowImplementationOptions.setActivityOptions(Map<String, ActivityOptions>)`. However, this is a static map keyed by activity type name — it cannot reflect per-node DSL values because the per-node values are only known at runtime when `executeNode` is called. This alternative is not viable for this feature.

---

## 4. Module Boundaries and Dependency Rules

| Artifact | Module | Dependency on |
|---|---|---|
| `NodeRetryConfig` | `commons` | None (pure POJO) |
| `WorkflowNode` (modified) | `commons` | `NodeRetryConfig` (same module) |
| `ActivityDefaultsConfig` | `worker` | None (pure POJO) |
| `DriftWorkerConfiguration` (modified) | `worker` | `ActivityDefaultsConfig` (same module) |
| `ActivityOptionsBuilder` | `worker` | `commons` (reads `WorkflowNode`, `NodeRetryConfig`), Temporal SDK |
| `ActivityOptionsBuilderHolder` | `worker` | `ActivityOptionsBuilder` (same module) |
| `WorkflowNodeExecutor` (modified) | `worker` | `ActivityOptionsBuilderHolder` (same module) |

**Critical invariant:** `commons` must not import anything from `worker`. `NodeRetryConfig` is a plain POJO with no Temporal SDK types. This preserves the existing one-way dependency (`worker` → `commons`).

---

## 5. Data Flow Diagram

```
                  HBase (DSL store)
                        │
                        │  JSON deserialisation (Jackson)
                        ▼
              WorkflowNode
              ├─ retryConfig: NodeRetryConfig  (nullable)
              └─ timeoutSeconds: Integer       (nullable)
                        │
                        │  passed to
                        ▼
              WorkflowNodeExecutor.executeNode()
                        │
                        │  external node?
                        ▼
              ActivityOptionsBuilder.build(node)
                        │
              ┌─────────┴──────────┐
              │                    │
     node-level config      ActivityDefaultsConfig
     (from WorkflowNode)    (from DriftWorkerConfiguration,
                             loaded from YAML at startup)
              │                    │
              └─────────┬──────────┘
                        │  merged (node-level wins)
                        ▼
              ActivityOptions  (timeout + retry policy)
                        │
                        ▼
              Temporal activity stub
              (external node execution)
```

---

## 6. Configuration Schema

### 6.1 WorkflowNode DSL (HBase JSON)

```json
{
  "instanceName": "call-vendor-api",
  "type": "HTTP",
  "resourceId": "vendor-http-node",
  "resourceVersion": "1.0",
  "timeoutSeconds": 45,
  "retryConfig": {
    "maxAttempts": 3,
    "initialIntervalSeconds": 2,
    "maxIntervalSeconds": 30,
    "backoffCoefficient": 2.0
  },
  "nextNode": "check-response"
}
```

All `retryConfig` sub-fields except `maxAttempts` are optional. An absent `retryConfig` object means "use platform defaults".

### 6.2 Dropwizard YAML (`configuration.yaml`)

```yaml
activityDefaults:
  defaultMaxAttempts: 1
  defaultTimeoutSeconds: 10
```

Both sub-fields are optional. Omitting `activityDefaults` entirely is valid; JVM defaults (1 attempt, 10 s) are applied, preserving existing behaviour.

---

## 7. Classes to Create / Modify

### New classes (commons module)

| Class | Package | Description |
|---|---|---|
| `NodeRetryConfig` | `com.flipkart.drift.commons.model.node` | POJO with `maxAttempts`, `initialIntervalSeconds`, `maxIntervalSeconds`, `backoffCoefficient`. Lombok `@Data @NoArgsConstructor @AllArgsConstructor`. `@JsonIgnoreProperties(ignoreUnknown = true)`. Default field values set on the Java fields so Jackson partial deserialization works. |

### New classes (worker module)

| Class | Package | Description |
|---|---|---|
| `ActivityDefaultsConfig` | `com.flipkart.drift.worker.config` | POJO with `defaultMaxAttempts` (default 1), `defaultTimeoutSeconds` (default 10). Lombok. `@JsonIgnoreProperties(ignoreUnknown = true)`. |
| `ActivityOptionsBuilder` | `com.flipkart.drift.worker.temporal` | Plain Java. Constructor takes `ActivityDefaultsConfig`. Single public method: `ActivityOptions build(WorkflowNode node)`. No Temporal / Spring / Guice annotations. |
| `ActivityOptionsBuilderHolder` | `com.flipkart.drift.worker.temporal` | Static holder. `static void init(ActivityOptionsBuilder)` called once at startup. `static ActivityOptionsBuilder get()` used by `WorkflowNodeExecutor`. Throws `IllegalStateException` if `get()` is called before `init()`. |

### Modified classes

| Class | Module | Change |
|---|---|---|
| `WorkflowNode` | commons | Add `NodeRetryConfig retryConfig` (nullable, `@JsonIgnoreProperties` already present). Add `Integer timeoutSeconds` (nullable). |
| `DriftWorkerConfiguration` | worker | Add `ActivityDefaultsConfig activityDefaults` (optional field, no `@NotNull`). |
| `WorkflowNodeExecutor` | worker | In `executeNode()` private method: replace `OptionsStore.activityOptionsV1` stub creation with `ActivityOptionsBuilderHolder.get().build(currentNode)`. No change to `executeWorkflowNode()` — that method is for disconnected nodes, which also use `activityOptionsV1` today and should receive the same builder treatment. |
| `TemporalWorkerManaged` | worker | In constructor (before `workerFactory.start()`), call `ActivityOptionsBuilderHolder.init(new ActivityOptionsBuilder(configuration.getActivityDefaults()))`. |

---

## 8. Unit Test Scope

`ActivityOptionsBuilder` is a plain Java class; it can be tested with zero Temporal infrastructure:

| Test case | Input | Expected `ActivityOptions` |
|---|---|---|
| Node-level timeout wins | `timeoutSeconds=45`, no defaults | `startToClose=45s` |
| Node-level retry wins | `retryConfig.maxAttempts=3`, defaults=1 | `maxAttempts=3` |
| Partial override (timeout only) | `timeoutSeconds=20`, `defaultMaxAttempts=2` | `timeout=20s`, `maxAttempts=2` |
| Partial override (retry only) | `retryConfig.maxAttempts=5`, `defaultTimeoutSeconds=30` | `timeout=30s`, `maxAttempts=5` |
| All defaults (no node config, no YAML) | null retryConfig, null timeout, null `ActivityDefaultsConfig` | `timeout=10s`, `maxAttempts=1` |
| YAML defaults only (no node config) | null retryConfig, null timeout, `defaultTimeout=30`, `defaultAttempts=2` | `timeout=30s`, `maxAttempts=2` |
| Full node override | all node fields set | node values used, platform ignored |

Existing tests in `WorkflowNodeExecutorTest` (if any) must pass unchanged — the fallback behaviour when `retryConfig` and `timeoutSeconds` are absent must be identical to the old `activityOptionsV1`.

---

## 9. Backward Compatibility

| Scenario | Behaviour |
|---|---|
| Existing DSL in HBase (no `retryConfig`, no `timeoutSeconds`) | Jackson deserializes both fields as `null`. `ActivityOptionsBuilder` falls back to `defaultMaxAttempts=1` and `defaultTimeoutSeconds=10` (same as `activityOptionsV1`). No behaviour change. |
| YAML with no `activityDefaults` section | `DriftWorkerConfiguration.getActivityDefaults()` returns `null` or a default-constructed `ActivityDefaultsConfig`. `ActivityOptionsBuilder` uses hardcoded JVM defaults (1 attempt, 10 s). No behaviour change. |
| Internal activities (ReturnControl, WCM, FetchWorkflow) | Not touched. Continue to use `OptionsStore.activityOptions` (30 min, 3 retries). |
| Local activities (INSTRUCTION, BRANCH, GROOVY, SUCCESS, FAILURE) | Not touched. Continue to use `OptionsStore.localActivityOptions`. |

---

## 10. Risks and Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Static holder initialised after worker starts polling (race condition) | Low | `ActivityOptionsBuilderHolder.init()` is called in `TemporalWorkerManaged.start()` before `workerFactory.start()`. Temporal begins polling only after `workerFactory.start()`. |
| `ActivityOptionsBuilderHolder.get()` called before `init()` (e.g. in a test) | Medium | `get()` throws `IllegalStateException` with clear message. Tests that directly instantiate `WorkflowNodeExecutor` must call `init()` in `@BeforeEach`. |
| Invalid `retryConfig` in DSL (e.g. `maxAttempts=0`) | Low | Temporal SDK validates `RetryOptions` at build time and throws `IllegalArgumentException`. The activity will fail fast with a non-retryable error rather than silently behaving incorrectly. |
| Large per-node timeout accidentally set (e.g. 24 hours) | Low | No guardrail in this version — out of scope. Workflow authors are responsible for reasonable values. |

---

## 11. Out of Scope (confirmed in PRD)

- `localActivityOptions` retry/timeout configuration
- Workflow-level retry/timeout (`WorkflowOptions`)
- Dynamic config refresh (Archaius)
- `retryConfig` on `NodeDefinition` subtypes
- UI changes to the Drift management console

---

## 12. Implementation Layers (for LLD)

The LLD will decompose work into the following layers in dependency order:

1. **Layer 1 — commons** — `NodeRetryConfig` (new POJO), `WorkflowNode` (add two fields)
2. **Layer 2 — worker config** — `ActivityDefaultsConfig` (new POJO), `DriftWorkerConfiguration` (add optional field)
3. **Layer 3 — worker temporal** — `ActivityOptionsBuilder` (new), `ActivityOptionsBuilderHolder` (new)
4. **Layer 4 — worker integration** — `WorkflowNodeExecutor` (replace `activityOptionsV1` stub), `TemporalWorkerManaged` (init holder), unit tests
