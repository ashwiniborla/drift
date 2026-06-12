# Low-Level Design: Propagate Entity Version on Publish

**Feature tag:** `publish-version-fix`
**Date:** 2026-06-12
**Status:** Active
**Author:** Ashwini Borla
**HLD:** `harness-docs/design/active/publish-version-fix-hld.md`
**Expanded PRD:** `harness-docs/design/active/publish-version-fix-prd-expanded.md`
**Affected modules:** `api`
**Risk level:** Very Low — 4 production files, 2 new test files, ~8 lines changed in prod

---

## Phase 1D: HLD Coverage Table

| HLD Requirement | HLD Source | LLD Coverage | Status |
|---|---|---|---|
| Change `publishNode` return type `void` → `NodeDefinition` | HLD §3 Changed: NodeDefinitionService | §2 NodeDefinitionService before/after pseudocode | COVERED |
| Change `publishWorkflow` return type `void` → `Workflow` | HLD §3 Changed: WorkflowDefinitionService | §2 WorkflowDefinitionService before/after pseudocode | COVERED |
| Capture `NodeDefinition` in resource handler, pass to `Response.ok()` | HLD §3 Changed: Resource handlers | §2 NodeDefinitionResource before/after pseudocode | COVERED |
| Capture `Workflow` in resource handler, pass to `Response.ok()` | HLD §3 Changed: Resource handlers | §2 WorkflowDefinitionResource before/after pseudocode | COVERED |
| Return in-memory entity (no extra DB read) — KDD-1 | HLD §8 KDD-1 | §2 — both `return nodeDefinition/workflow` are the already-built local vars | COVERED |
| SC-3: version = "1" first publish, incremented on subsequent | HLD §1 SC-3 | §7 Test Scenarios — publishNode_firstPublish, publishWorkflow_subsequentPublish | COVERED |
| SC-4: HTTP 200 unchanged | HLD §1 SC-4 | §2 resource pseudocode — `Response.ok(...).build()` unchanged status | COVERED |
| SC-5: HTTP 500 on error path unchanged | HLD §1 SC-5 | §2 — catch block and Response.status(500) call untouched | COVERED |
| SC-7: All new unit tests pass | HLD §1 SC-7 | §7 Test Scenarios + §9 Verification command | COVERED |

---

## Section 1: Files Changed

### Production (modified)

| File | Change summary |
|---|---|
| `api/src/main/java/com/flipkart/drift/api/service/builder/NodeDefinitionService.java` | `publishNode`: `void` → `NodeDefinition`; 2 `return` statements |
| `api/src/main/java/com/flipkart/drift/api/service/builder/WorkflowDefinitionService.java` | `publishWorkflow`: `void` → `Workflow`; 2 `return` statements |
| `api/src/main/java/com/flipkart/drift/api/resources/NodeDefinitionResource.java` | Capture `NodeDefinition`; `Response.ok(nodeDefinition).build()` |
| `api/src/main/java/com/flipkart/drift/api/resources/WorkflowDefinitionResource.java` | Capture `Workflow`; `Response.ok(workflow).build()` |

### Test (new)

| File | Package |
|---|---|
| `api/src/test/java/com/flipkart/drift/api/service/builder/NodeDefinitionServiceTest.java` | `com.flipkart.drift.api.service.builder` |
| `api/src/test/java/com/flipkart/drift/api/service/builder/WorkflowDefinitionServiceTest.java` | `com.flipkart.drift.api.service.builder` |

---

## Section 2: Exact Code Changes (Before / After)

### 2.1 NodeDefinitionService.java

**Before (line 67):**
```java
public void publishNode(String id) {
    try {
        ...
        if (latestNodeHB == null) {
            ...
            return;         // ← Branch A early return
        }
        ...
        updateNodeInHBase(...);
        publishRedisEvent(...);
        publishRedisEvent(...);
        // ← Branch B falls through (void)
    } catch (Exception e) {
        throw new ApiException("Error while publishing node in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
    }
}
```

**After:**
```java
public NodeDefinition publishNode(String id) {
    try {
        ...
        if (latestNodeHB == null) {
            ...
            return nodeDefinition;   // ← Branch A
        }
        ...
        updateNodeInHBase(...);
        publishRedisEvent(...);
        publishRedisEvent(...);
        return nodeDefinition;       // ← Branch B
    } catch (Exception e) {
        throw new ApiException("Error while publishing node in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
    }
}
```

**Diff summary:** 3 lines changed/added; all logic unchanged.

---

### 2.2 WorkflowDefinitionService.java

**Before (line 225):**
```java
public void publishWorkflow(String id) {
    try {
        ...
        if (latestWorkflowHB == null) {
            ...
            return;         // ← Branch A early return
        }
        ...
        updateWorkflowInHBase(...);
        publishRedisEvent(...);
        publishRedisEvent(...);
        // ← Branch B falls through (void)
    } catch (Exception e) {
        throw new ApiException("Error while publishing workflow in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
    }
}
```

**After:**
```java
public Workflow publishWorkflow(String id) {
    try {
        ...
        if (latestWorkflowHB == null) {
            ...
            return workflow;   // ← Branch A
        }
        ...
        updateWorkflowInHBase(...);
        publishRedisEvent(...);
        publishRedisEvent(...);
        return workflow;       // ← Branch B
    } catch (Exception e) {
        throw new ApiException("Error while publishing workflow in HBase", Response.Status.INTERNAL_SERVER_ERROR, e);
    }
}
```

---

### 2.3 NodeDefinitionResource.java

**Before (lines 72-74):**
```java
try {
    nodeDefinitionService.publishNode(id);
    return Response.ok().build();
}
```

**After:**
```java
try {
    NodeDefinition nodeDefinition = nodeDefinitionService.publishNode(id);
    return Response.ok(nodeDefinition).build();
}
```

---

### 2.4 WorkflowDefinitionResource.java

**Before (lines 71-73):**
```java
try {
    workflowDefinitionService.publishWorkflow(id);
    return Response.ok().build();
}
```

**After:**
```java
try {
    Workflow workflow = workflowDefinitionService.publishWorkflow(id);
    return Response.ok(workflow).build();
}
```

---

## Section 3: API Contract Specifications

| Endpoint | Method | Old Response | New Response | Status code |
|---|---|---|---|---|
| `/nodeDefinition/{id}/publishNode/` | POST | 200 OK, empty body | 200 OK, `NodeDefinition` JSON | Unchanged |
| `/workflowDefinition/{id}/publishWorkflow/` | POST | 200 OK, empty body | 200 OK, `Workflow` JSON | Unchanged |
| Both endpoints on error | POST | 500, error message string | 500, error message string | Unchanged |

---

## Section 4: Call Graph

```
POST /nodeDefinition/{id}/publishNode/
  NodeDefinitionResource.publishNode(id)
    → NodeDefinitionService.publishNode(id) : NodeDefinition   [CHANGED return type]
        getNodeHB(snapshotKey)               [unchanged]
        nodeDefinitionDao.get(latestKey)     [unchanged]
        nodeDefinition.setVersion(...)       [unchanged — already happens]
        createNode / updateNodeInHBase       [unchanged]
        publishRedisEvent × 2               [unchanged]
        return nodeDefinition                [NEW]
    NodeDefinition nodeDefinition = ...      [NEW capture]
    Response.ok(nodeDefinition).build()      [CHANGED from .ok()]

POST /workflowDefinition/{id}/publishWorkflow/
  WorkflowDefinitionResource.publishWorkflow(id)
    → WorkflowDefinitionService.publishWorkflow(id) : Workflow  [CHANGED return type]
        [same pattern as above]
        return workflow                      [NEW]
    Workflow workflow = ...                  [NEW capture]
    Response.ok(workflow).build()            [CHANGED from .ok()]
```

---

## Section 5: Edge Cases

| Case | Behaviour after fix |
|---|---|
| First publish (no prior LATEST record) | Returns `nodeDefinition`/`workflow` with `version = "1"` |
| Subsequent publish | Returns entity with version incremented from LATEST |
| Exception during HBase/Redis call | `ApiException` thrown → resource catches → HTTP 500 (unchanged) |
| `latestNodeHB.getNodeData()` returns entity with null version | `StringToIntegerVersionParser` (pre-existing) handles gracefully; fix does not touch this path |

---

## Section 6: Observability

No changes to logging. Both service methods have no `log.info/debug` calls for the publish path. The resource handler in `NodeDefinitionResource` logs `log.error("Error publishing node", e)` on exception — this is unchanged.

`WorkflowDefinitionResource.publishWorkflow` has no logging today — this is pre-existing and out of scope.

---

## Section 7: Test Scenarios

### 7.1 NodeDefinitionServiceTest

**Framework:** JUnit 5 (`@ExtendWith(MockitoExtension.class)`) + Mockito — consistent with `TemporalServiceTest`

**Mocks:**
```java
@Mock NodeDefinitionDao nodeDefinitionDao;
@Mock JedisSentinelPool jedisSentinelPool;
@Mock Jedis jedis;
@Mock ObjectMapper objectMapper;
```

**BeforeEach setup:**
```java
service = new NodeDefinitionService(nodeDefinitionDao, objectMapper, jedisSentinelPool);
when(jedisSentinelPool.getResource()).thenReturn(jedis);
```

**Concrete subtype used in tests:** `SuccessNode` (simplest `NodeDefinition` subclass; no extra required fields)

**Helper to build NodeHB:**
```java
private NodeHB nodeHBWith(NodeDefinition nd) {
    NodeHB hb = new NodeHB();
    hb.setNodeKey("key");
    hb.setNodeData(nd);
    return hb;
}
```

**Test 1: `publishNode_firstPublish_returnsNodeWithVersionOne`**
```
given: snapshotNodeHB → SuccessNode(id="n1", no version)
       latestNodeHB   → null
when: publishNode("n1")
then: returned.getVersion() == "1"
```

**Test 2: `publishNode_subsequentPublish_returnsNodeWithIncrementedVersion`**
```
given: snapshotNodeHB → SuccessNode(id="n1", no version)
       latestNodeHB   → SuccessNode with version="3"
when: publishNode("n1")
then: returned.getVersion() == "4"
```

**Test 3: `publishNode_exceptionFromDao_throwsApiException`**
```
given: nodeDefinitionDao.get(snapshotKey) throws IOException("hbase read failed")
when: publishNode("n1")
then: assertThrows(ApiException.class)
      ex.getStatus() == INTERNAL_SERVER_ERROR
```

---

### 7.2 WorkflowDefinitionServiceTest

**Mocks:**
```java
@Mock WorkflowDefinitionDao workflowDefinitionDao;
@Mock JedisSentinelPool jedisSentinelPool;
@Mock Jedis jedis;
@Mock ObjectMapper objectMapper;
@Mock NodeDefinitionService nodeDefinitionService;
```

**BeforeEach setup:**
```java
service = new WorkflowDefinitionService(workflowDefinitionDao, objectMapper, jedisSentinelPool, nodeDefinitionService);
when(jedisSentinelPool.getResource()).thenReturn(jedis);
```

**Concrete type used:** `Workflow` (concrete `@Data` class, no abstract methods)

**Test 1: `publishWorkflow_firstPublish_returnsWorkflowWithVersionOne`**
```
given: snapshotWorkflowHB → Workflow(id="wf1", no version)
       latestWorkflowHB   → null
when: publishWorkflow("wf1")
then: returned.getVersion() == "1"
```

**Test 2: `publishWorkflow_subsequentPublish_returnsWorkflowWithIncrementedVersion`**
```
given: snapshotWorkflowHB → Workflow(id="wf1", no version)
       latestWorkflowHB   → Workflow with version="5"
when: publishWorkflow("wf1")
then: returned.getVersion() == "6"
```

**Test 3: `publishWorkflow_exceptionFromDao_throwsApiException`**
```
given: workflowDefinitionDao.get(snapshotKey) throws IOException("hbase read failed")
when: publishWorkflow("wf1")
then: assertThrows(ApiException.class)
      ex.getStatus() == INTERNAL_SERVER_ERROR
```

---

## Section 8: Key Design Decisions (Implementation Level)

### KDD-1: `return nodeDefinition` uses the local variable, not a re-fetch

The `nodeDefinition` local variable is extracted from the snapshot `NodeHB` and then mutated with `setVersion()`. After the persistence calls, this variable holds exactly what was written. Returning it directly avoids an HBase round-trip (per PRD NFR-4).

### KDD-2: `WorkflowHB.getWorkflowData()` / `NodeHB.getNodeData()` use static Jackson

Both HB entities hold their data as serialised JSON strings and use a class-level `ObjectMapper` for `setNodeData`/`getNodeData`. The test helper calls `setNodeData()` with a fully constructed `SuccessNode`/`Workflow`, which serialises to JSON. When the service calls `getNodeData()`, it deserialises back. This round-trip through Jackson requires no special mocking — `NodeHB` and `WorkflowHB` use real `ObjectMapper` instances internally.

### KDD-3: Test mocks `Jedis` returned by `JedisSentinelPool.getResource()`

`publishRedisEvent(jedisSentinelPool, channel, msg)` calls `jedisSentinelPool.getResource()` → `jedis.publish(channel, msg)` → `jedis.close()`. Mocking `JedisSentinelPool` and stubbing `getResource()` to return a mock `Jedis` prevents NullPointerException and isolates tests from Redis.

---

## Section 9: Verification

```bash
# Run only the new tests
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  mvn test -pl api -P '!delombok-for-javadoc' \
  -Dtest="NodeDefinitionServiceTest,WorkflowDefinitionServiceTest" \
  -Dsurefire.failIfNoSpecifiedTests=false

# Full api module compile check
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  mvn compile -pl api -P '!delombok-for-javadoc'
```

Expected: BUILD SUCCESS, 6 tests pass (3 per service).

---

## Section 10: Revision History

| Version | Date | Author | Description |
|---|---|---|---|
| 1.0 | 2026-06-12 | Ashwini Borla | Initial LLD for `publish-version-fix` |
