# PRD: defaultFailureNode Type Correctness
**Status:** Draft  
**Date:** 2026-06-03  
**Author:** Ashwini Borla

---

## 1. Background

Drift allows any workflow node to be designated as the `defaultFailureNode` — the node that runs when another node throws an exception. There is no type restriction: `GROOVY`, `HTTP`, `BRANCH`, `SUCCESS`, or `FAILURE` nodes can all be configured here.

The platform's failure routing in `WorkflowNodeExecutor.handleNodeExecutionError()` was built with `FAILURE`-type nodes in mind. A `FAILURE` node explicitly returns `WorkflowStatus.FAILED`, which causes `updateWorkflowState()` to set the final status correctly and `handleFailedState()` to call `ReturnControlActivity` — unblocking the API caller.

When a **non-`FAILURE`-type node** is used as `defaultFailureNode`, the status returned by that node is blindly applied via `updateWorkflowState()`, replacing whatever the failure routing intended. This produces two distinct breakage patterns:

**Pattern A — Stuck in RUNNING (GROOVY, HTTP, BRANCH, INSTRUCTION nodes):**  
These nodes return `WorkflowStatus.RUNNING`. After `updateWorkflowState()` sets `status = RUNNING`, `handleNodeResponseStatus()` hits the `default` case — it only logs a warning and never calls `ReturnControlActivity`. The Temporal workflow loop then exits with `currentNode = null`, the Temporal execution closes normally, and the `workflowState` is permanently stuck at `RUNNING`. The API caller receives a 500 timeout.

**Pattern B — Silent failure swallow (SUCCESS node, SYNC mode):**  
A `SUCCESS` node returns `WorkflowStatus.COMPLETED`. After `updateWorkflowState()` sets `status = COMPLETED`, `handleCompletedState()` calls `ReturnControlActivity` — unblocking the API caller with `status: COMPLETED`. The underlying HTTP (or other node) failure is completely hidden. The caller has no indication the workflow failed.

The root cause in both cases: `executeWorkflowNodes()` treats the `defaultFailureNode` identically to any normal node — it applies `updateWorkflowState()` and `handleNodeResponseStatus()` using the fallback node's response, losing the failure context entirely.

---

## 2. Goals

- **Correct terminal status**: Regardless of the `defaultFailureNode` type, the final `workflowState.status` must always be `FAILED` after a node failure routes through `defaultFailureNode`.
- **API caller unblocked**: `ReturnControlActivity` must always be called after the `defaultFailureNode` completes, so the API does not time out.
- **FAILURE-type behaviour preserved**: The existing behaviour when `defaultFailureNode` is a `FAILURE` node must not change — `FAILURE` nodes are the canonical case and their error response should still propagate.
- **defaultFailureNode still executes**: The `defaultFailureNode` should still run (for side effects, alerting, HBase writes, etc.) — it should not be skipped.

---

## 3. Non-Goals

- Restricting or validating which node types may be used as `defaultFailureNode` at workflow-creation time.
- Changing retry or timeout behaviour for the `defaultFailureNode` itself.
- Modifying how `SuccessNode`, `TerminatedNode`, or non-failure terminal paths behave outside the `defaultFailureNode` context.
- Altering `ReturnControlActivity` or `RedisPubSubService` timeout configuration.

---

## 4. Requirements

### 4.1 Execute defaultFailureNode but Ignore Its Returned Status

**Location:** `GenericWorkflowImpl.executeWorkflowNodes()` — [GenericWorkflowImpl.java:103](../worker/src/main/java/com/flipkart/drift/worker/workflows/GenericWorkflowImpl.java)

**Current behaviour:**  
After the `defaultFailureNode` executes, `handleNodeResponseStatus()` is called with the fallback node's response. The returned `WorkflowStatus` (RUNNING, COMPLETED, etc.) overwrites the workflow's status via `updateWorkflowState()`.

**Required behaviour:**  
Before executing any node, check whether it is the `defaultFailureNode`. If it is:
1. Execute the node normally (for side effects).
2. After it completes, **do not** call `handleNodeResponseStatus()` — instead, force `status = FAILED`, call `ReturnControlActivity` to unblock the API, and throw `ApplicationFailure` to terminate the Temporal workflow.

```java
private void executeWorkflowNodes(Workflow workflow, WorkflowNode currentNode, String workflowId,
                                   Map<String, String> threadContext, WorkflowStartRequest workflowStartRequest) {
    while (currentNode != null) {
        boolean isDefaultFailureNode = currentNode.getInstanceName().equals(workflow.getDefaultFailureNode());
        ActivityThinResponse activityThinResponse;
        try {
            logger.info("WfId : {} Running node: {}", workflowId, currentNode.getInstanceName());
            activityThinResponse = nodeExecutor.executeNode(currentNode, threadContext, workflowStartRequest);
        } catch (Exception e) {
            if (isDefaultFailureNode) {
                // defaultFailureNode itself failed — stop immediately, do not loop
                this.workflowState.setStatus(WorkflowStatus.FAILED);
                this.workflowState.setErrorMessage("defaultFailureNode failed: " + e.getMessage());
                throw ApplicationFailure.newNonRetryableFailureWithCause(
                    "defaultFailureNode failed, aborting to prevent infinite loop",
                    "FAILURE_NODE_FAILED", e);
            }
            currentNode = nodeExecutor.handleNodeExecutionError(e, currentNode, workflow);
            continue;
        }

        if (activityThinResponse != null) {
            if (isDefaultFailureNode) {
                // defaultFailureNode ran — force FAILED terminal state regardless of node type
                this.workflowState.setStatus(WorkflowStatus.FAILED);
                io.temporal.workflow.Workflow.newActivityStub(ReturnControlActivity.class, OptionsStore.activityOptions)
                    .exec(workflowId);
                throw ApplicationFailure.newNonRetryableFailure(
                    "Workflow failed — defaultFailureNode completed", "WORKFLOW_FAILED_VIA_FALLBACK");
            }
            nodeExecutor.handleNodeResponseStatus(workflowId, activityThinResponse, workflow, threadContext);
        }
        currentNode = workflow.getStates().get(currentNode.getNextNode());
    }
}
```

### 4.2 Set FAILED Status at Point of Node Failure (prerequisite)

**Location:** `WorkflowNodeExecutor.handleNodeExecutionError()` — [WorkflowNodeExecutor.java:142](../worker/src/main/java/com/flipkart/drift/worker/workflows/WorkflowNodeExecutor.java)

`status = FAILED` must be set before the `defaultFailureNode` runs, so any concurrent query sees the correct status during the fallback execution window.

```java
public WorkflowNode handleNodeExecutionError(Exception e, WorkflowNode failedNode, Workflow workflow) {
    this.workflowState.setStatus(WorkflowStatus.FAILED);                    // ← set immediately
    this.workflowState.setCurrentNodeRef(generateNodeIdentifier(failedNode)); // ← record failing node
    this.workflowState.setErrorMessage("Error message: " + e.getMessage());
    WorkflowNode fallbackNode = workflow.getStates().get(workflow.getDefaultFailureNode());
    if (fallbackNode == null) {
        throw ApplicationFailure.newNonRetryableFailureWithCause(
            "Failed to execute node: " + e.getMessage(),
            "NODE_EXECUTION_FAILED", e
        );
    }
    return fallbackNode;
}
```

Note: the method signature adds `WorkflowNode failedNode` — the call site in `executeWorkflowNodes()` must pass `currentNode` as the second argument.

### 4.3 FAILURE-Type defaultFailureNode: Preserve Existing Path

When `defaultFailureNode` is a `FAILURE` node, requirement 4.1 replaces the previous `handleFailedState()` path. The error response from the `FAILURE` node (used in `handleFailedState()` to set `errorMessage`) is no longer applied. Since requirement 4.2 already sets `errorMessage` from the original failing node's exception, this is the correct behaviour — the original error is preserved, not overwritten by the `FAILURE` node's generic message.

### 4.4 No-defaultFailureNode Path Unchanged

When `workflow.getDefaultFailureNode()` is `null` or maps to no node, `handleNodeExecutionError()` already correctly sets `FAILED` and throws `ApplicationFailure`. Requirement 4.2 moves the `setStatus(FAILED)` call before the branch, which is the only change on this path.

---

## 5. Root Cause Summary

| Step | Before fix | After fix |
|---|---|---|
| Node fails | `handleNodeExecutionError` sets `errorMessage`, returns fallback | `handleNodeExecutionError` sets `FAILED` + `currentNodeRef` + `errorMessage`, returns fallback |
| defaultFailureNode executes | `updateWorkflowState` blindly applies node's returned status (RUNNING / COMPLETED) | Status not updated from node response — already FAILED |
| After defaultFailureNode | `handleNodeResponseStatus` routes on returned status (wrong) | Force `status=FAILED`, call `ReturnControlActivity`, throw `ApplicationFailure` |
| API caller | RUNNING (timeout 500) or COMPLETED (silent swallow) | FAILED — always correct |

---

## 6. Observed Symptoms (from Live Testing — 2026-06-03)

**GROOVY node as defaultFailureNode (test: `test-groovy-failure-run-001`):**

| Field | Expected | Actual |
|---|---|---|
| API response | 200 with `status: FAILED` | 500 timeout after 5 seconds |
| `status` after | `FAILED` | `RUNNING` (stuck forever) |
| `currentNodeRef` | failing HTTP node | GROOVY node |
| `errorMessage` | HTTP error message | HTTP error message (correctly set by handleNodeExecutionError) |

**Worker log execution order (GROOVY case):**
```
Error executing node start-http-node
Running node: groovy-failure-node
[no ReturnControlActivity call]
[Temporal workflow completes with status = RUNNING]
```

**SUCCESS node as defaultFailureNode (code-confirmed):**

| Field | Expected | Actual |
|---|---|---|
| API response | `status: FAILED` | 200 with `status: COMPLETED` |
| `status` after | `FAILED` | `COMPLETED` |
| `currentNodeRef` | failing HTTP node | SUCCESS node |
| `errorMessage` | HTTP error message | HTTP error message (set, but status masks it) |

---

## 7. Files to Change

| File | Change |
|---|---|
| `worker/src/main/java/.../workflows/GenericWorkflowImpl.java` | Add `isDefaultFailureNode` check in `executeWorkflowNodes()`; force `FAILED` + `ReturnControlActivity` + `ApplicationFailure` after fallback completes; add infinite-loop guard in catch block |
| `worker/src/main/java/.../workflows/WorkflowNodeExecutor.java` | Add `setStatus(FAILED)` + `setCurrentNodeRef(failedNode)` at top of `handleNodeExecutionError()`; update method signature to accept `WorkflowNode failedNode` |

---

## 8. Verification

1. **Unit test — GROOVY defaultFailureNode**: Configure workflow with GROOVY as `defaultFailureNode`. Stub HTTP node to throw. Assert `workflowState.status == FAILED` after execution. Assert `ReturnControlActivity` was called exactly once.

2. **Unit test — SUCCESS defaultFailureNode (SYNC)**: Same setup with SUCCESS node. Assert `workflowState.status == FAILED`, not `COMPLETED`. Assert API is unblocked (ReturnControlActivity called).

3. **Unit test — FAILURE defaultFailureNode (regression)**: Same setup with FAILURE node. Assert `workflowState.status == FAILED`. Assert `currentNodeRef` is the failing HTTP node, not the FAILURE node.

4. **Unit test — infinite-loop guard**: Stub both start-node and `defaultFailureNode` to throw. Assert workflow terminates with `ApplicationFailure`, not infinite loop.

5. **Unit test — no-defaultFailureNode path unchanged**: Stub workflow with no `defaultFailureNode`. Assert `FAILED` is set + `ApplicationFailure` thrown — unchanged behaviour.

6. **Integration test — GROOVY**: Trigger `test-groovy-failure-run-002` after the fix. Assert:
   - API returns within 5 seconds (not a timeout)
   - `GET /v3/workflow/{workflowId}` returns `status: FAILED`
   - `currentNodeRef` is the HTTP node that failed, not the GROOVY node

7. **Integration test — SUCCESS**: Trigger `test-success-failure-run-001` after the fix. Assert:
   - `GET /v3/workflow/{workflowId}` returns `status: FAILED`, not `COMPLETED`
