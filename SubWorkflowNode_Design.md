# SubWorkflowNode - Complete Design Document

## 1. Concept

SubWorkflowNode is a new node type that **inlines/flattens** a referenced child workflow into the parent workflow at **DSL fetch time**. Unlike the existing `ChildNode` (which spawns a separate Temporal workflow), SubWorkflowNode merges the child workflow's nodes directly into the parent's execution graph.

```
W1: INS_W1 → Groovy_W1 → SubWorkflowNode(W2) → END_W1
W2: INS_W2 → Groovy_W2 → END_W2

Flattened: INS_W1 → Groovy_W1 → sub_w2_INS_W2 → sub_w2_Groovy_W2 → sub_w2_END_W2 → END_W1

With includeLastNode=false:
INS_W1 → Groovy_W1 → sub_w2_INS_W2 → sub_w2_Groovy_W2 → END_W1
```

---

## 2. Configuration (Top 4 - Extensible)

| Config | Type | Default | Description |
|--------|------|---------|-------------|
| `includeLastNode` | Boolean | true | Include terminal node (SUCCESS/FAILURE) of the sub-workflow |
| `includeFirstNode` | Boolean | true | Include start node of the sub-workflow |
| `contextKeyPrefix` | String | null | Custom prefix for all inlined node context keys. If null, uses SubWorkflowNode's contextOverrideKey or instanceName |
| `errorHandlingStrategy` | Enum | PROPAGATE | How failures inside the inlined sub-workflow are handled at runtime. See **Error handling strategy** below. |

### Error Handling Strategy

Controls what happens when a node *inside* the inlined sub-workflow fails at runtime.

| Value | Meaning | Implementation status |
|-------|--------|------------------------|
| **PROPAGATE** (default) | Failures in the inlined sub-workflow are treated as failures of the parent. The **parent workflow's** `defaultFailureNode` is used to route to the failure handler. There is no separate failure scope for the sub-workflow. | **Implemented.** Runtime uses the root workflow's defaultFailureNode only. |
| **ISOLATE** | Failures in the inlined sub-workflow should first be handled by the **sub-workflow's own** `defaultFailureNode` (if defined); that failure node would be inlined into the same flattened graph. If the sub-workflow has no defaultFailureNode, or outside its scope, fall back to the parent's defaultFailureNode. Use when the sub-workflow should have its own failure handling before escalating to the parent. | **Not implemented.** Config is accepted but runtime behaviour is the same as PROPAGATE (parent's defaultFailureNode only). Future: inline sub-workflow's defaultFailureNode and use per-sub-workflow failure scope. |

The flattener does *not* apply `errorHandlingStrategy` during merge (it does not wire failure scopes or inline sub-workflow failure nodes). Failure handling is entirely at execution time; currently only PROPAGATE behaviour is supported.

### Context Key Override

Each node's default context key = `instanceName`. With contextOverrideKey on SubWorkflowNode:

| SubWorkflowNode instanceName | contextOverrideKey | config.contextKeyPrefix | Inlined "groovy_1" key |
|----|----|----|----|
| sub_w2 | null | null | sub_w2_groovy_1 |
| sub_w2 | xyz | null | xyz_groovy_1 |
| sub_w2 | xyz | custom | custom_groovy_1 |

---

## 3. New Classes

### 3.1 SubWorkflowNode (`commons/model/node/SubWorkflowNode.java`)

```java
@Data @NoArgsConstructor
public class SubWorkflowNode extends NodeDefinition {
    @NotBlank private String subWorkflowId;
    @NotBlank private String subWorkflowVersion;
    @Valid private SubWorkflowConfig config;

    @Override public NodeType getType() { return NodeType.SUB_WORKFLOW; }
    // + validateWFNodeFields(), mergeRequestToEntity()
}
```

### 3.2 SubWorkflowConfig (`commons/model/subworkflow/SubWorkflowConfig.java`)

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubWorkflowConfig {
    @Builder.Default private Boolean includeLastNode = true;
    @Builder.Default private Boolean includeFirstNode = true;
    private String contextKeyPrefix;
    @Builder.Default private ErrorHandlingStrategy errorHandlingStrategy = ErrorHandlingStrategy.PROPAGATE;
}
```

### 3.3 ErrorHandlingStrategy (`commons/model/enums/ErrorHandlingStrategy.java`)

Enum with Javadoc describing each value. **PROPAGATE**: use parent's defaultFailureNode (implemented). **ISOLATE**: use sub-workflow's defaultFailureNode first (not implemented; behaves like PROPAGATE).

### 3.4 SubWorkflowFlattener (`worker/service/SubWorkflowFlattener.java`)

Core service that performs recursive flattening with cycle detection.

---

## 4. Flattening Algorithm

### 4.1 Entry Point (FetchWorkflowActivityImpl)

After enriching nodes with NodeDefinitions:
```java
workflow = subWorkflowFlattener.flattenWorkflow(deepCopy(workflow), tenant, new HashSet<>(), 0);
```

### 4.2 Recursive Flatten

```
flattenWorkflow(workflow, tenant, visitedSet, depth):
    if depth > MAX_DEPTH (10): throw MaxDepthExceededException

    collect all SUBWORKFLOW nodes from workflow.states (snapshot to avoid ConcurrentModification)

    for each subWorkflowNode:
        subWorkflowId = subWorkflowNode.nodeDefinition.subWorkflowId
        if subWorkflowId in visitedSet: throw CircularReferenceException

        visitedSet.add(subWorkflowId)
        subWorkflow = deepCopy(fetchAndEnrich(subWorkflowId, subWorkflowVersion, tenant))
        flattenWorkflow(subWorkflow, tenant, visitedSet, depth + 1)  // recurse first

        inlineSubWorkflow(workflow, subWorkflowNode, subWorkflow, config)
        visitedSet.remove(subWorkflowId)

    return workflow
```

### 4.3 Inline Steps

1. **Compute prefix**: config.contextKeyPrefix → contextOverrideKey → instanceName
2. **Build name mapping**: oldName → prefix_oldName for all sub-workflow nodes
3. **Deep-copy and prefix** each WorkflowNode:
   - `instanceName` = prefixed name
   - `contextOverrideKey` = prefix_originalContextOverrideKey (or prefix_originalInstanceName)
   - `nextNode` = nameMapping.get(nextNode)
4. **Deep-copy and prefix NodeDefinitions** that have internal references:
   - BranchNode: prefix `choices[].nextNode` and `defaultNode`
   - ProcessorNode: prefix `instructionNodeRef`
5. **Handle includeFirstNode/includeLastNode**: exclude nodes, adjust effective start/end
6. **Rewire parent**: predecessor.nextNode → effectiveStart; effectiveEnd.nextNode → SubWorkflowNode.nextNode
7. **Handle postWorkflowCompletionNodes**: prefix and merge into parent's list
8. **Handle ISOLATE errorHandling**: inline sub-workflow's defaultFailureNode, add to per-scope failure map
9. **Remove SubWorkflowNode** from parent states

---

## 5. Critical Design Constraints (from Review Agent)

### CONSTRAINT 1: Never Mutate Cached Objects
`WorkflowCache` and `NodeDefinitionCache` return shared objects. Flattening MUST operate on deep copies.

### CONSTRAINT 2: NodeDefinition References Must Be Prefixed
BranchNode (`choices[].nextNode`, `defaultNode`), ProcessorNode (`instructionNodeRef`) store references inside NodeDefinition. These must be deep-copied and prefixed for inlined nodes.

### CONSTRAINT 3: Context Key Consistency
`generateNodeIdentifier()` exists in BOTH `WorkflowNodeExecutor` AND `BaseNodeActivityImpl`. Both must produce the same key. Set `contextOverrideKey` on inlined nodes during flattening.

### CONSTRAINT 4: Temporal Determinism
Flattening happens inside `FetchWorkflowActivity` (a Temporal activity), which is safe. Never do flattening inside workflow code.

### CONSTRAINT 5: Unique State Keys
All keys in `workflow.getStates()` must be unique, even across multiple inlined instances of the same sub-workflow.

---

## 6. Where It Can Break - Complete Scenario Analysis

### CRITICAL Scenarios

| # | Scenario | Risk | Mitigation |
|---|----------|------|------------|
| 1 | **Circular references** (W1→W2→W1) | Infinite recursion | visitedSet cycle detection |
| 2 | **BranchNode inside sub-workflow** | choices[].nextNode are unprefixed in cached NodeDefinition | Deep-copy NodeDefinition, prefix all references |
| 3 | **ProcessorNode instructionNodeRef** | Reads context using unprefixed key | Deep-copy NodeDefinition, prefix instructionNodeRef |
| 4 | **WAITING resume in sub-workflow** | currentNodeRef must be prefixed name | Set contextOverrideKey = prefixed name during flattening |
| 5 | **Cache mutation** | Mutating cached workflow/NodeDefinition corrupts cache | Always deep-copy before flattening |
| 6 | **ISOLATE error handling** | Workflow has single defaultFailureNode | Add subWorkflowFailureNodeOverrides map on Workflow |

### HIGH Scenarios

| # | Scenario | Risk | Mitigation |
|---|----------|------|------------|
| 7 | **includeLastNode=false** | Predecessor's nextNode not updated | Flattener tracks effective end, rewires predecessor |
| 8 | **includeFirstNode=false** | Parent's nextNode not updated | Flattener computes effective start from sub-workflow's startNode.nextNode |
| 9 | **PostWorkflowCompletionNodes** in sub-workflow | Not in parent's list | Prefix and merge into parent's list |
| 10 | **Multiple SubWorkflowNodes referencing same workflow** | Name collisions | Each instance gets unique prefix from instanceName |
| 11 | **Parent branch targeting SubWorkflowNode** | Branch target becomes invalid after removal | Rewrite all references to SubWorkflowNode → effective start |
| 12 | **Activity nextNode override** (BranchNode) | Returns unprefixed name | Deep-copy BranchNode NodeDefinition with prefixed targets |

### MEDIUM Scenarios

| # | Scenario | Risk | Mitigation |
|---|----------|------|------------|
| 13 | **Empty/single-node sub-workflow** | No nodes to inline | Connect predecessor directly to successor |
| 14 | **Both includeFirst+Last=false, 2-node sub-workflow** | Zero inlined nodes | Handle gracefully, direct connection |
| 15 | **Diagram generation** | SubWorkflowNode type not handled | Add shape/color for SUB_WORKFLOW in WorkflowGraphNode |
| 16 | **executeDisconnectedNode** | Caller must pass prefixed name | Document or validate |
| 17 | **Deeply nested sub-workflows** | Performance, context size | Max depth=10, monitor HBase context size |

### Nesting-Specific Scenarios

| Scenario | Detail |
|----------|--------|
| W1→SubWf(W2)→SubWf(W3) | Double prefix: sub_w2_sub_w3_X |
| Resume WAITING in nested sub-workflow | currentNodeRef = sub_w2_sub_w3_INSTRUCTION_X, must match exactly on resume |
| Nested cycle: W3→SubWf(W1) | Detected by visitedSet containing W1 |
| Nested ISOLATE error handling | Each nesting level may have its own failure scope |

---

## 7. Files to Modify

| File | Change |
|------|--------|
| `NodeType.java` | Add `SUB_WORKFLOW` |
| `NodeDefinition.java` | Add `@JsonSubTypes.Type(value = SubWorkflowNode.class, name = "SUB_WORKFLOW")` |
| **NEW** `SubWorkflowNode.java` | Node model |
| **NEW** `SubWorkflowConfig.java` | Configuration model |
| **NEW** `ErrorHandlingStrategy.java` | Enum |
| **NEW** `SubWorkflowFlattener.java` | Core flattening service |
| `FetchWorkflowActivityImpl.java` | Inject flattener, call after enrichment |
| `WorkflowNodeExecutor.java` | Add SUB_WORKFLOW safety guard (should never reach execution) |
| `WorkflowDefinitionService.java` | Publish-time validation, diagram support |
| `WorkflowGraphNode.java` | Shape/color for SUB_WORKFLOW |
| `Workflow.java` | (Optional) Add `subWorkflowFailureNodeOverrides` map for ISOLATE |
| Worker Guice module | Bind SubWorkflowFlattener |

---

## 8. Implementation Order

1. Add `SUB_WORKFLOW` to `NodeType` enum
2. Create `ErrorHandlingStrategy` enum
3. Create `SubWorkflowConfig` class
4. Create `SubWorkflowNode` class (extends NodeDefinition)
5. Register in `NodeDefinition` @JsonSubTypes
6. Create `SubWorkflowFlattener` service
7. Integrate into `FetchWorkflowActivityImpl` (deep-copy + flatten)
8. Add safety guard in `WorkflowNodeExecutor`
9. Add validation in `WorkflowDefinitionService`
10. Update diagram generation
11. Implement ISOLATE error handling (optional, can be Phase 2)
12. Write comprehensive tests

---

## 9. Test Cases (SubWorkflowFlattenerTest)

The merge path is covered by unit tests in `worker/src/test/java/com/flipkart/drift/worker/service/SubWorkflowFlattenerTest.java`. The following scenarios are implemented.

### 9.1 Group 1: Basic Include/Exclude Chaining

| Test Method | Config | Verifies |
|-------------|--------|----------|
| `includeAll_inlinesAllNodes` | includeFirst=true, includeLast=true | All sub-workflow nodes inlined; linear chain preserved |
| `excludeFirst_skipsFirstNode` | includeFirst=false, includeLast=true | First node excluded; effective start = second node |
| `excludeLast_skipsLastNode` | includeFirst=true, includeLast=false | Last node excluded; effective end = predecessor of terminal |
| `excludeBoth_skipsFirstAndLastNodes` | includeFirst=false, includeLast=false | Only middle nodes inlined |

### 9.2 Group 2: Small Sub-Workflow Edge Cases

| Test Method | Verifies |
|-------------|----------|
| `singleNode_includeAll` | Single-node sub with includeAll inlined correctly |
| `singleNode_excludeBoth_emptyScope_passThrough` | Single node + both excluded → empty scope; references rewired to successor, sub skipped |
| `twoNodes_excludeBoth_emptyScope_passThrough` | Two-node sub with both excluded → empty scope; pass-through |

### 9.3 Group 3: Nested (Multi-Level) Sub-Workflows

| Test Method | Verifies |
|-------------|----------|
| `twoLevelNesting` | A → sub_b → a2; B contains sub_c; C inlined into B, then B into A; full chain correct |
| `threeLevelNesting_allIncluded` | A → B → C → D (all includeFirst/includeLast true); B, C, D all inlined; no false duplicate |
| `threeLevelNesting_mixedConfigs` | A → B(T,F) → C(F,T) → D(T,T); mixed configs per level; chain and branch targets correct |

### 9.4 Group 4: Branch Rewiring

| Test Method | Verifies |
|-------------|----------|
| `branchChoice_rewiredToSubStart` | Parent branch choice pointing at SubWorkflowNode rewired to sub’s effective start |
| `branchDefault_rewiredToSubStart` | Parent branch defaultNode pointing at SubWorkflowNode rewired to effective start |
| `multipleReferences_allRewired` | nextNode, branch choices, and defaultNode all pointing at sub; all rewired |

### 9.5 Group 5: Sub at Start/End of Parent

| Test Method | Verifies |
|-------------|----------|
| `subIsStartNode_startNodeUpdated` | When SubWorkflowNode is workflow startNode, parent.startNode set to effective start |
| `subIsTerminal_endFlagPropagated` | Sub as terminal (end=true); inlined chain end gets correct end/nextNode |

### 9.6 Group 6: Sequential Subs in Same Parent

| Test Method | Verifies |
|-------------|----------|
| `sequentialSubs_bothInlined` | A: a1 → sub_b → sub_c → a4; B and C both inlined in order |

### 9.7 Group 7: Duplicate Detection (Error Cases)

| Test Method | Verifies |
|-------------|----------|
| `duplicateNodeName_acrossSubs_fails` | Two sibling subs (B, C) both contribute same node id → SUB_WORKFLOW_DUPLICATE_NODE_NAME |
| `duplicateContextKey_fails` | Two sibling subs with same contextOverrideKey → SUB_WORKFLOW_DUPLICATE_CONTEXT_KEY |
| `nestedInlining_noFalseDuplicate` | A → B → C; C has node "d1". When B and C inlined into A, "d1" registered once (no false duplicate) |

### 9.8 Group 8: Circular Reference / Max Depth (Error Cases)

| Test Method | Verifies |
|-------------|----------|
| `circularDirect_fails` | Workflow references itself → SUB_WORKFLOW_CIRCULAR_REFERENCE |
| `circularIndirect_fails` | A → B → A → SUB_WORKFLOW_CIRCULAR_REFERENCE |
| `maxDepthExceeded_fails` | Chain of 12 workflows; depth 11 exceeds MAX_DEPTH=10 → SUB_WORKFLOW_MAX_DEPTH_EXCEEDED |

### 9.9 Group 9: Special Behavior

| Test Method | Verifies |
|-------------|----------|
| `branchStartNode_notExcludedByIncludeFirstFalse` | Sub starts with BRANCH node; includeFirst=false does not exclude BRANCH (special case in InlineScope) |
| `postCompletionNodes_mergedToParent` | Sub’s postWorkflowCompletionNodes merged into parent when nodes are inlined |

---

## 10. Testing Checklist (Coverage vs. Tests)

| Scenario | Covered by Test |
|----------|-----------------|
| Basic flattening (W1 includes W2) | Yes — `includeAll_inlinesAllNodes`, `sequentialSubs_bothInlined` |
| includeLastNode=false | Yes — `excludeLast_skipsLastNode`, `threeLevelNesting_mixedConfigs` |
| includeFirstNode=false | Yes — `excludeFirst_skipsFirstNode`, `threeLevelNesting_mixedConfigs`, `branchStartNode_notExcludedByIncludeFirstFalse` |
| Both include=false | Yes — `excludeBoth_skipsFirstAndLastNodes`, `singleNode_excludeBoth_emptyScope_passThrough`, `twoNodes_excludeBoth_emptyScope_passThrough` |
| Nesting (W1→W2→W3) | Yes — `twoLevelNesting`, `threeLevelNesting_allIncluded`, `threeLevelNesting_mixedConfigs`, `nestedInlining_noFalseDuplicate` |
| Circular reference detection | Yes — `circularDirect_fails`, `circularIndirect_fails` |
| Max depth enforcement | Yes — `maxDepthExceeded_fails` |
| Branch node inside sub-workflow | Yes — `branchStartNode_notExcludedByIncludeFirstFalse` |
| Parent branch targeting SubWorkflowNode | Yes — `branchChoice_rewiredToSubStart`, `branchDefault_rewiredToSubStart`, `multipleReferences_allRewired` |
| ProcessorNode instructionNodeRef in sub-workflow | No — out of scope per product decision |
| WAITING state in sub-workflow + resume | No — integration/E2E |
| SCHEDULER_WAITING in sub-workflow | No — integration/E2E |
| Multiple SubWorkflowNodes referencing same workflow | Yes — `sequentialSubs_bothInlined`; duplicate name across different subs: `duplicateNodeName_acrossSubs_fails` |
| Context key override / duplicate context key | Yes — `duplicateContextKey_fails` |
| Context key prefix | No — not implemented in current flattener (no prefixing of inlined names) |
| Empty sub-workflow | Yes — `singleNode_excludeBoth_emptyScope_passThrough`, `twoNodes_excludeBoth_emptyScope_passThrough` |
| Error handling PROPAGATE | Not explicitly tested (no failure path in unit tests) |
| Error handling ISOLATE | Not implemented / not tested |
| Cache not mutated | No — fetch is mocked; caller must pass deep copy |
| PostWorkflowCompletionNodes handling | Yes — `postCompletionNodes_mergedToParent` |
