# Execution Plan: Retry and Timeout Configuration for Drift Workflows

**Feature tag:** retry-timeout-config
**Branch:** feature/retry-timeout-config
**Author:** Ashwini Borla
**Date:** 2026-05-19
**Status:** PLAN — ready for execution
**LLD:** [retry-timeout-config-lld.md](https://flipkart.atlassian.net/wiki/pages/viewpage.action?pageId=475268668)

---

## 1. Summary

This plan decomposes the LLD into 6 atomic subtasks across 4 layers. Each subtask maps to one git commit. The build command for all subtasks is:

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  mvn clean package -Dmaven.test.skip=true -P '!delombok-for-javadoc' -pl java-sdk,commons,api,worker -am -q
```

Tests run only in Layer 4 (after all production code is in place):

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  mvn test -pl worker -am -P '!delombok-for-javadoc' -q
```

---

## 2. Layer Summary

| Layer | Label | Subtasks | Commit |
|---|---|---|---|
| 1 | commons | ST-1: NodeRetryConfig + WorkflowNode | 1 commit |
| 2 | worker-config | ST-2: ActivityDefaultsConfig + DriftWorkerConfiguration + YAML | 1 commit |
| 3 | worker-temporal | ST-3: ActivityOptionsBuilder + ActivityOptionsBuilderHolder | 1 commit |
| 4 | worker-integration | ST-4: TemporalWorkerManaged, ST-5: WorkflowNodeExecutor, ST-6: ActivityOptionsBuilderTest | 1 commit (all integration changes together) |

---

## 3. Subtask Details

### ST-1 — Layer 1: commons — NodeRetryConfig + WorkflowNode

**Files:**
- CREATE `commons/src/main/java/com/flipkart/drift/commons/model/node/NodeRetryConfig.java`
- MODIFY `commons/src/main/java/com/flipkart/drift/commons/model/node/WorkflowNode.java`

**What to implement:**

**NodeRetryConfig.java** — new file, exact content from LLD Section 3.1:
- Package: `com.flipkart.drift.commons.model.node`
- Annotations: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@JsonIgnoreProperties(ignoreUnknown = true)`
- Fields: `int maxAttempts` (`@JsonProperty("maxAttempts")`), `int initialIntervalSeconds = 1` (`@JsonProperty("initialIntervalSeconds")`), `int maxIntervalSeconds = 20` (`@JsonProperty("maxIntervalSeconds")`), `double backoffCoefficient = 2.0` (`@JsonProperty("backoffCoefficient")`)
- No Temporal SDK imports — commons must not depend on Temporal.

**WorkflowNode.java** — add two fields after the existing `end` field:
- Add import: `import com.fasterxml.jackson.annotation.JsonProperty;`
- Add import: `import com.flipkart.drift.commons.model.node.NodeRetryConfig;` (same package, not strictly needed but makes intent explicit — omit if the compiler complains about same-package import)
- Add field: `@JsonProperty("timeoutSeconds") private Integer timeoutSeconds;`
- Add field: `@JsonProperty("retryConfig") private NodeRetryConfig retryConfig;`
- The `@AllArgsConstructor` Lombok annotation is already present — after adding two fields, the generated constructor will have 11 parameters. This is fine; the constructor is not called anywhere except tests (and those tests construct via setters).

**Build gate:** `mvn clean package -Dmaven.test.skip=true -P '!delombok-for-javadoc' -pl java-sdk,commons,api,worker -am -q` — must exit 0.

**Acceptance criteria:**
- `NodeRetryConfig` compiles with no errors
- `WorkflowNode` compiles with the two new fields
- `@AllArgsConstructor` on `WorkflowNode` generates a constructor that includes `timeoutSeconds` and `retryConfig`
- No Temporal imports anywhere in `commons`

---

### ST-2 — Layer 2: worker-config — ActivityDefaultsConfig + DriftWorkerConfiguration + YAML

**Files:**
- CREATE `worker/src/main/java/com/flipkart/drift/worker/config/ActivityDefaultsConfig.java`
- MODIFY `worker/src/main/java/com/flipkart/drift/worker/config/DriftWorkerConfiguration.java`
- MODIFY `worker/src/main/resources/config/configuration.yaml`

**What to implement:**

**ActivityDefaultsConfig.java** — new file, exact content from LLD Section 3.2:
- Package: `com.flipkart.drift.worker.config`
- Annotations: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@JsonIgnoreProperties(ignoreUnknown = true)`
- Fields: `int defaultMaxAttempts = 1` (`@JsonProperty("defaultMaxAttempts")`), `int defaultTimeoutSeconds = 10` (`@JsonProperty("defaultTimeoutSeconds")`)

**DriftWorkerConfiguration.java** — add one field at the end of the field block (before the closing brace):
```java
private ActivityDefaultsConfig activityDefaults;
```
No `@NotNull` — this field is optional. `@Getter` and `@Setter` are already on the class via Lombok.
`ActivityDefaultsConfig` is in the same package — no import needed.

**configuration.yaml** — add after the `workerDynamicOptions` block:
```yaml
# Optional — per-node activity defaults for external-node Temporal stubs.
# Omitting this block is valid; JVM defaults (1 attempt, 10 s) apply.
activityDefaults:
  defaultMaxAttempts: 1
  defaultTimeoutSeconds: 10
```

**Build gate:** `mvn clean package -Dmaven.test.skip=true -P '!delombok-for-javadoc' -pl java-sdk,commons,api,worker -am -q` — must exit 0.

**Acceptance criteria:**
- `ActivityDefaultsConfig` compiles with no errors
- `DriftWorkerConfiguration` compiles with the new `activityDefaults` field
- `getActivityDefaults()` and `setActivityDefaults()` are generated by Lombok (verified by `mvn clean package`)
- YAML is valid (no parse errors when loaded by Dropwizard)

---

### ST-3 — Layer 3: worker-temporal — ActivityOptionsBuilder + ActivityOptionsBuilderHolder

**Files:**
- CREATE `worker/src/main/java/com/flipkart/drift/worker/temporal/ActivityOptionsBuilder.java`
- CREATE `worker/src/main/java/com/flipkart/drift/worker/temporal/ActivityOptionsBuilderHolder.java`

**What to implement:**

**ActivityOptionsBuilder.java** — new file, exact content from LLD Section 3.3:
- Package: `com.flipkart.drift.worker.temporal`
- Imports: `NodeRetryConfig`, `WorkflowNode`, `ActivityDefaultsConfig`, `ActivityOptions`, `RetryOptions`, `Duration`
- Constants: `FALLBACK_TIMEOUT_SECONDS=10`, `FALLBACK_MAX_ATTEMPTS=1`, `FALLBACK_INITIAL_INTERVAL_SECONDS=1`, `FALLBACK_MAX_INTERVAL_SECONDS=20`, `FALLBACK_BACKOFF_COEFFICIENT=2.0`
- Constructor accepts `ActivityDefaultsConfig defaults` (nullable — null treated as absent YAML block)
- `build(WorkflowNode node)` — merges node-level config with platform defaults using the priority rules in the LLD

**ActivityOptionsBuilderHolder.java** — new file, exact content from LLD Section 3.4:
- Package: `com.flipkart.drift.worker.temporal`
- `private static volatile ActivityOptionsBuilder INSTANCE = null`
- `public static void init(ActivityOptionsBuilder builder)` — sets INSTANCE (replaces any existing)
- `public static ActivityOptionsBuilder get()` — returns INSTANCE, throws `IllegalStateException` if null
- Private constructor — static holder, no instances

**Build gate:** `mvn clean package -Dmaven.test.skip=true -P '!delombok-for-javadoc' -pl java-sdk,commons,api,worker -am -q` — must exit 0.

**Acceptance criteria:**
- Both classes compile with no errors
- `ActivityOptionsBuilder` has no Temporal or Guice annotations on the class itself
- `ActivityOptionsBuilderHolder.get()` throws `IllegalStateException` when INSTANCE is null (verified structurally)
- `ActivityOptionsBuilderHolder.init(null)` would set INSTANCE to null — this is intentional for test cleanup

---

### ST-4 — Layer 4a: worker-integration — TemporalWorkerManaged

**File:**
- MODIFY `worker/src/main/java/com/flipkart/drift/worker/bootstrap/TemporalWorkerManaged.java`

**What to implement (exact diff from LLD Section 4.3):**

1. Add field after `private final long terminationTimeoutInSec;`:
   ```java
   private final DriftWorkerConfiguration configuration;
   ```

2. In the constructor, add `this.configuration = configuration;` as the FIRST line (before the `createWorkerFactory` call):
   ```java
   public TemporalWorkerManaged(Injector injector, DriftWorkerConfiguration configuration, Scope metricsScope) {
       this.configuration = configuration;
       this.workerFactory = createWorkerFactory(injector, configuration, metricsScope);
       this.terminationTimeoutInSec = configuration.getAwaitTerminationTimeoutInSec();
   }
   ```

3. In `start()`, add `ActivityOptionsBuilderHolder.init(...)` BEFORE `workerFactory.start()`:
   ```java
   @Override
   public void start() {
       log.info("Starting Temporal Worker");
       ActivityOptionsBuilderHolder.init(
               new ActivityOptionsBuilder(this.configuration.getActivityDefaults()));
       workerFactory.start();
       log.info("Started Temporal Worker !!!!");
   }
   ```

4. Add imports:
   ```java
   import com.flipkart.drift.worker.temporal.ActivityOptionsBuilder;
   import com.flipkart.drift.worker.temporal.ActivityOptionsBuilderHolder;
   ```

**Critical ordering invariant:** `init()` must run before `workerFactory.start()`. Do not swap these lines.

**Build gate:** `mvn clean package -Dmaven.test.skip=true -P '!delombok-for-javadoc' -pl java-sdk,commons,api,worker -am -q` — must exit 0.

**Acceptance criteria:**
- `TemporalWorkerManaged` compiles
- `configuration` field is set before `createWorkerFactory` is called
- `ActivityOptionsBuilderHolder.init(...)` is called before `workerFactory.start()` in `start()`

---

### ST-5 — Layer 4b: worker-integration — WorkflowNodeExecutor

**File:**
- MODIFY `worker/src/main/java/com/flipkart/drift/worker/workflows/WorkflowNodeExecutor.java`

**What to implement (exact diff from LLD Section 4.4):**

**Change 1 — `executeNode()` private method (line ~82):**

Replace:
```java
ActivityStub activityStub = isLocalActivity ?
        io.temporal.workflow.Workflow.newUntypedLocalActivityStub(OptionsStore.localActivityOptions) :
        io.temporal.workflow.Workflow.newUntypedActivityStub(OptionsStore.activityOptionsV1);
```

With:
```java
ActivityStub activityStub = isLocalActivity ?
        io.temporal.workflow.Workflow.newUntypedLocalActivityStub(OptionsStore.localActivityOptions) :
        io.temporal.workflow.Workflow.newUntypedActivityStub(
                ActivityOptionsBuilderHolder.get().build(currentNode));
```

**Change 2 — `executeWorkflowNode()` (disconnected nodes, line ~158):**

Replace:
```java
ActivityStub untypedActivityStub = io.temporal.workflow.Workflow.newUntypedActivityStub(OptionsStore.activityOptionsV1);
```

With:
```java
ActivityStub untypedActivityStub = io.temporal.workflow.Workflow.newUntypedActivityStub(
        ActivityOptionsBuilderHolder.get().build(workflowNode));
```

**Add import:**
```java
import com.flipkart.drift.worker.temporal.ActivityOptionsBuilderHolder;
```

**Do NOT remove** the `import com.flipkart.drift.worker.temporal.OptionsStore;` import — `OptionsStore` is still used for `localActivityOptions` (line ~81) and for all `ReturnControlActivity`, `WorkflowContextManagerActivity`, and `FetchWorkflowActivity` stubs which continue to use `OptionsStore.activityOptions`.

**Build gate:** `mvn clean package -Dmaven.test.skip=true -P '!delombok-for-javadoc' -pl java-sdk,commons,api,worker -am -q` — must exit 0.

**Acceptance criteria:**
- `WorkflowNodeExecutor` compiles
- The two `OptionsStore.activityOptionsV1` references are gone from the file
- `OptionsStore.localActivityOptions` reference remains (local activities unchanged)
- All `OptionsStore.activityOptions` references remain (internal activities unchanged)

---

### ST-6 — Layer 4c: worker-integration — ActivityOptionsBuilderTest

**File:**
- CREATE `worker/src/test/java/com/flipkart/drift/worker/temporal/ActivityOptionsBuilderTest.java`

**What to implement (exact content from LLD Section 7):**

The test directory `worker/src/test/java/com/flipkart/drift/worker/temporal/` does not currently exist — create it.

The test class is a plain JUnit 5 class with 7 test cases (TC-1 through TC-7). No Mockito, no Temporal test server.

Test cases:
- TC-1: `nodeLevelTimeoutWins` — node timeout=45, no retry → options timeout=45s, maxAttempts=1
- TC-2: `nodeLevelRetryWins` — no timeout, retry maxAttempts=3 → options timeout=10s, maxAttempts=3
- TC-3: `partialOverrideTimeoutOnly` — timeout=20, no retry, defaults=(2, 10) → timeout=20s, maxAttempts=2
- TC-4: `partialOverrideRetryOnly` — no timeout, retry maxAttempts=5, defaults=(1, 30) → timeout=30s, maxAttempts=5
- TC-5: `allDefaultsNoNodeConfigNoYaml` — null defaults, null node config → timeout=10s, maxAttempts=1
- TC-6: `yamlDefaultsOnlyNoNodeConfig` — defaults=(2, 30), no node config → timeout=30s, maxAttempts=2
- TC-7: `fullNodeOverride` — all node fields set (timeout=60, retry=(5,3,60,1.5)), defaults=(1,10) → verifies all 5 fields

**Test gate:** `mvn test -pl worker -am -P '!delombok-for-javadoc' -q` — all 7 tests must pass, zero failures.

**Acceptance criteria:**
- All 7 test cases pass
- No compilation errors
- No Temporal test server required (plain JUnit 5)

---

## 4. ArchUnit Regression

After ST-6, verify the existing arch rules still pass:

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  mvn test -pl worker -am -P '!delombok-for-javadoc' -q -Dtest=WorkerArchTest
```

All four existing rules must pass (see LLD Section 8 for rationale):
- Rule 1: worker must not depend on api
- Rule 2: no `System.currentTimeMillis()` in workflows package
- Rule 3: no `Thread.sleep()` in workflows package
- Rule 4: no direct HBase `Table` access

---

## 5. Commit Plan

| Commit | Subtasks | Message |
|---|---|---|
| Commit 1 | ST-1 | `feat(commons): add NodeRetryConfig POJO and timeoutSeconds/retryConfig fields on WorkflowNode` |
| Commit 2 | ST-2 | `feat(worker-config): add ActivityDefaultsConfig and activityDefaults field in DriftWorkerConfiguration` |
| Commit 3 | ST-3 | `feat(worker-temporal): add ActivityOptionsBuilder and ActivityOptionsBuilderHolder` |
| Commit 4 | ST-4 + ST-5 + ST-6 | `feat(worker-integration): wire ActivityOptionsBuilder into TemporalWorkerManaged and WorkflowNodeExecutor; add ActivityOptionsBuilderTest` |

---

## 6. Definition of Done

- [ ] All 4 commits land on `feature/retry-timeout-config`
- [ ] `mvn clean package -Dmaven.test.skip=true -P '!delombok-for-javadoc' -pl java-sdk,commons,api,worker -am -q` exits 0
- [ ] `mvn test -pl worker -am -P '!delombok-for-javadoc' -q` exits 0 (7 new tests pass, all existing tests pass)
- [ ] `WorkerArchTest` passes (4 arch rules)
- [ ] `OptionsStore.activityOptionsV1` has zero references in `WorkflowNodeExecutor.java`
- [ ] `OptionsStore.localActivityOptions` and `OptionsStore.activityOptions` references are untouched
- [ ] `configuration.yaml` contains the `activityDefaults` block
