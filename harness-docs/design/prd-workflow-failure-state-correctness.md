# PRD: Workflow Failure State Correctness
**Status:** Draft  
**Date:** 2026-06-03  
**Author:** Ashwini Borla

---

## 1. Background

Drift executes workflows using Temporal. When a workflow node throws an exception (e.g., an HTTP call fails), the platform routes execution to a `defaultFailureNode` — a special node defined in the workflow DSL that is responsible for handling failure scenarios. This is the primary failure-handling mechanism.

The bug was discovered and confirmed via a live test: a workflow was constructed with a `defaultFailureNode` (pointing to a `FailureNode`) and a start node pointing to an HTTP endpoint that always fails. After triggering execution, the resulting `WorkflowState` had three problems:

1. **`status`** was not set to `FAILED` at the point of failure — only after the `defaultFailureNode` completed.
2. **`errorMessage`** was `"null"` in the final state — the actual error from the failing node was lost.
3. **`currentNodeRef`** pointed to `"default-failure"` (the `defaultFailureNode`) rather than `"start-http-node"` (the node that actually failed).

The root cause is in `WorkflowNodeExecutor.handleNodeExecutionError()`, which sets the `errorMessage` but does **not** set `status = FAILED` before delegating to the `defaultFailureNode`. The `FAILED` status only materialises later, when `FailureNodeNodeActivityImpl` completes and `updateWorkflowState()` is called — a window during which the state is stale and misleading.

---

## 2. Goals

- **Immediate failure state**: Set `workflowState.status = FAILED` at the exact point `handleNodeExecutionError` is called, regardless of whether a `defaultFailureNode` is configured.
- **Preserved error context**: Ensure the `errorMessage` from the actual failing node is retained and surfaced in the final `WorkflowState`.
- **Accurate node reference**: Record the `instanceName` of the node that failed as `currentNodeRef`, not the `defaultFailureNode`.
- **Infinite-loop guard**: Prevent the execution loop from re-entering `handleNodeExecutionError` with the same `defaultFailureNode` if the failure node itself throws.

---

## 3. Non-Goals

- Changing the behaviour of the `defaultFailureNode` execution flow itself — it should still run after failure.
- Modifying per-node retry or timeout configuration (covered in a separate PRD).
- Changing how `SuccessNode`, `TerminatedNode`, or any non-failure terminal paths behave.
- Altering how internal/infrastructure activities (`FetchWorkflowActivity`, `WorkflowContextManagerActivity`) report failure.

---

## 4. Requirements

### 4.1 Immediate FAILED Status on Node Failure

**Location:** `WorkflowNodeExecutor.handleNodeExecutionError()` — [WorkflowNodeExecutor.java:142](../worker/src/main/java/com/flipkart/drift/worker/workflows/WorkflowNodeExecutor.java)

**Current behaviour:**
```java
public WorkflowNode handleNodeExecutionError(Exception e, Workflow workflow) {
    this.workflowState.setErrorMessage("Error message: " + e.getMessage());
    WorkflowNode fallbackNode = workflow.getStates().get(workflow.getDefaultFailureNode());
    if (fallbackNode == null) {
        this.workflowState.setStatus(WorkflowStatus.FAILED);   // ← only set here
        throw ApplicationFailure.newNonRetryableFailureWithCause(...);
    }
    return fallbackNode;  // ← no status set on this path
}
```

**Required behaviour:**  
`workflowState.status` must be set to `FAILED` **before** returning the `fallbackNode`, so that any concurrent state query (or a query after the workflow completes) sees the correct terminal status immediately — not a stale `CREATED` or `RUNNING`.

```java
public WorkflowNode handleNodeExecutionError(Exception e, Workflow workflow) {
    this.workflowState.setStatus(WorkflowStatus.FAILED);           // ← move here
    this.workflowState.setErrorMessage("Error message: " + e.getMessage());
    WorkflowNode fallbackNode = workflow.getStates().get(workflow.getDefaultFailureNode());
    if (fallbackNode == null) {
        throw ApplicationFailure.newNonRetryableFailureWithCause(...);
    }
    return fallbackNode;
}
```

### 4.2 Record Failing Node as currentNodeRef

**Location:** `WorkflowNodeExecutor.handleNodeExecutionError()` and `GenericWorkflowImpl.executeWorkflowNodes()` — [GenericWorkflowImpl.java:103](../worker/src/main/java/com/flipkart/drift/worker/workflows/GenericWorkflowImpl.java)

**Current behaviour:**  
`currentNodeRef` is only set inside `updateWorkflowState()`, which is only called on a **successful** activity response. When a node fails, `updateWorkflowState()` is never called for that node, so `currentNodeRef` ends up pointing to the `defaultFailureNode` (the last node that completed successfully).

**Required behaviour:**  
When `handleNodeExecutionError` is called, set `currentNodeRef` to the `instanceName` of the node that failed. This gives operators an accurate signal for which node caused the workflow to enter a failed state.

`handleNodeExecutionError` must receive the `currentNode` (the failing `WorkflowNode`) so it can record it:

```java
// GenericWorkflowImpl.executeWorkflowNodes() — call site change
} catch (Exception e) {
    currentNode = nodeExecutor.handleNodeExecutionError(e, currentNode, workflow);
    continue;
}

// WorkflowNodeExecutor — updated signature
public WorkflowNode handleNodeExecutionError(Exception e, WorkflowNode failedNode, Workflow workflow) {
    this.workflowState.setStatus(WorkflowStatus.FAILED);
    this.workflowState.setCurrentNodeRef(generateNodeIdentifier(failedNode));  // ← failing node
    this.workflowState.setErrorMessage("Error message: " + e.getMessage());
    WorkflowNode fallbackNode = workflow.getStates().get(workflow.getDefaultFailureNode());
    if (fallbackNode == null) {
        throw ApplicationFailure.newNonRetryableFailureWithCause(...);
    }
    return fallbackNode;
}
```

### 4.3 Infinite-Loop Guard for defaultFailureNode Failure

**Location:** `GenericWorkflowImpl.executeWorkflowNodes()` — [GenericWorkflowImpl.java:109](../worker/src/main/java/com/flipkart/drift/worker/workflows/GenericWorkflowImpl.java)

**Current behaviour:**  
If the `defaultFailureNode` itself throws during execution, `handleNodeExecutionError` is called again, which returns the same `defaultFailureNode` — causing an infinite execution loop.

**Required behaviour:**  
Before executing any node, check whether it is the configured `defaultFailureNode`. If the failing node IS the `defaultFailureNode`, skip the `handleNodeExecutionError` delegation and terminate immediately with a non-retryable `ApplicationFailure`.

```java
private void executeWorkflowNodes(Workflow workflow, WorkflowNode currentNode, ...) {
    while (currentNode != null) {
        ActivityThinResponse activityThinResponse;
        try {
            activityThinResponse = nodeExecutor.executeNode(currentNode, ...);
        } catch (Exception e) {
            // Guard: if the failing node is itself the defaultFailureNode, stop immediately
            if (currentNode.getInstanceName().equals(workflow.getDefaultFailureNode())) {
                this.workflowState.setStatus(WorkflowStatus.FAILED);
                this.workflowState.setErrorMessage("defaultFailureNode itself failed: " + e.getMessage());
                throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "defaultFailureNode failed, aborting to prevent infinite loop",
                    "FAILURE_NODE_FAILED", e);
            }
            currentNode = nodeExecutor.handleNodeExecutionError(e, currentNode, workflow);
            continue;
        }
        ...
    }
}
```

### 4.4 No Regression on the No-defaultFailureNode Path

The existing path where `defaultFailureNode` is absent must remain unchanged. `handleNodeExecutionError` already correctly sets `FAILED` and throws `ApplicationFailure` in that case — the only change is moving the `setStatus(FAILED)` call to happen unconditionally before the fallback-node branch.

---

## 5. Observed Symptoms (from Live Test — 2026-06-03)

The following was confirmed by running `test-failure-status-run-002` against a workflow with a `bad-http-node` (connection refused) and `default-failure` as `defaultFailureNode`:

| Field | Expected | Actual |
|---|---|---|
| `status` | `FAILED` (immediately on node failure) | `FAILED` (only after defaultFailureNode ran) |
| `errorMessage` | Error message from the HTTP failure | `"null"` |
| `currentNodeRef` | `"start-http-node"` (the failing node) | `"default-failure"` (the defaultFailureNode) |

**Worker log execution order (confirms status gap):**
```
08:15:34,934  Error executing node start-http-node      ← node fails, status still CREATED
08:15:34,934  Running node: default-failure             ← status not yet FAILED
08:15:35,231  Publishing redis event                    ← only now does status become FAILED
```

---

## 6. Files to Change

| File | Change |
|---|---|
| `worker/src/main/java/.../workflows/WorkflowNodeExecutor.java` | Add `setStatus(FAILED)` + `setCurrentNodeRef(failedNode)` in `handleNodeExecutionError`; update method signature |
| `worker/src/main/java/.../workflows/GenericWorkflowImpl.java` | Pass `currentNode` to `handleNodeExecutionError`; add infinite-loop guard |

---

## 7. Verification

1. **Unit test — status set immediately**: Mock a workflow with `defaultFailureNode` configured. Stub the start-node activity to throw. Assert `workflowState.status == FAILED` is set inside `handleNodeExecutionError`, before the fallback node runs.

2. **Unit test — currentNodeRef accuracy**: Same setup as above. Assert `workflowState.currentNodeRef` equals the failing node's `instanceName`, not the defaultFailureNode's.

3. **Unit test — infinite-loop guard**: Stub both the start-node and the defaultFailureNode to throw. Assert the workflow terminates with a non-retryable `ApplicationFailure` rather than looping.

4. **Unit test — no-defaultFailureNode path unchanged**: Stub a workflow with no `defaultFailureNode`. Assert existing behavior: `FAILED` set + `ApplicationFailure` thrown, no `currentNodeRef` set.

5. **Integration test**: Run `test-failure-status-run-002` (or equivalent) after the fix. Assert:
   - `GET /v3/workflow/{workflowId}` returns `status: FAILED`, `currentNodeRef: start-http-node`, and a non-null `errorMessage`.
   - Temporal UI shows the correct event ordering.
