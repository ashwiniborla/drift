# Expanded PRD: Propagate Entity Version on Publish

**Feature tag:** `publish-version-fix`
**Date:** 2026-06-12
**Status:** Active
**Affected modules:** `api`
**Risk level:** Very Low — 4 files, ~8 lines changed, pure return-type wiring, no logic change

---

## 1. Executive Summary

`NodeDefinitionService.publishNode` and `WorkflowDefinitionService.publishWorkflow` both compute and persist a new version number internally, but declare `void` return types. Because they return nothing, the REST resource handlers have no data to serialise and build empty HTTP 200 responses (`Response.ok().build()`). Clients receive no indication of which version was assigned.

The version value is already computed and set on the entity object before any persistence call. Surfacing it requires only two changes: (1) update the service methods to return the hydrated entity instead of `void`, and (2) update the resource handlers to capture and serialise the returned entity. Zero additional database queries or compute cycles are introduced.

---

## 2. Actors & Systems

| Actor / System | Role |
|---|---|
| **Drift API service** (`api` module) | Exposes node and workflow management HTTP endpoints |
| **`NodeDefinitionService`** | Computes version, persists node to HBase, raises Redis event |
| **`WorkflowDefinitionService`** | Computes version, persists workflow to HBase, raises Redis event |
| **`NodeDefinitionResource`** | REST handler for node endpoints; currently returns empty 200 on publish |
| **`WorkflowDefinitionResource`** | REST handler for workflow endpoints; currently returns empty 200 on publish |
| **API clients / operators** | Consume HTTP publish responses; need the version field to track published state |
| **`NodeDefinition`** | Abstract model class with a `version` String field |
| **`Workflow`** | Concrete model class with a `version` String field |

---

## 3. Core Requirements

### FR-1 — Change `NodeDefinitionService.publishNode` return type

**File:** `api/src/main/java/com/flipkart/drift/api/service/builder/NodeDefinitionService.java`

Change:
```java
public void publishNode(String id)
```
to:
```java
public NodeDefinition publishNode(String id)
```

Both internal branches (first publish and subsequent publish) must return the `nodeDefinition` object after setting the version field. The existing early `return;` in the first-publish branch becomes `return nodeDefinition;`. A new `return nodeDefinition;` is appended after the second branch's final statement.

### FR-2 — Change `WorkflowDefinitionService.publishWorkflow` return type

**File:** `api/src/main/java/com/flipkart/drift/api/service/builder/WorkflowDefinitionService.java`

Change:
```java
public void publishWorkflow(String id)
```
to:
```java
public Workflow publishWorkflow(String id)
```

Same two-branch return pattern as FR-1.

### FR-3 — Update `NodeDefinitionResource.publishNode` to return entity

**File:** `api/src/main/java/com/flipkart/drift/api/resources/NodeDefinitionResource.java`

Change:
```java
nodeDefinitionService.publishNode(id);
return Response.ok().build();
```
to:
```java
NodeDefinition nodeDefinition = nodeDefinitionService.publishNode(id);
return Response.ok(nodeDefinition).build();
```

### FR-4 — Update `WorkflowDefinitionResource.publishWorkflow` to return entity

**File:** `api/src/main/java/com/flipkart/drift/api/resources/WorkflowDefinitionResource.java`

Change:
```java
workflowDefinitionService.publishWorkflow(id);
return Response.ok().build();
```
to:
```java
Workflow workflow = workflowDefinitionService.publishWorkflow(id);
return Response.ok(workflow).build();
```

### FR-5 — Add unit tests asserting on returned entity

New test classes:
- `api/src/test/java/com/flipkart/drift/api/service/builder/NodeDefinitionServiceTest.java`
- `api/src/test/java/com/flipkart/drift/api/service/builder/WorkflowDefinitionServiceTest.java`

Tests must assert that `publishNode` and `publishWorkflow` return the entity with the correct `version` field value (`"1"` on first publish, incremented integer string on subsequent publishes).

---

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | **Zero regression:** existing behaviour of addNode, updateNode, addWorkflow, updateWorkflow unchanged |
| NFR-2 | **HTTP status code unchanged:** publish endpoints continue to return HTTP 200 on success |
| NFR-3 | **Response format consistency:** the publish response JSON matches the schema already returned by add/update endpoints (same `NodeDefinition`/`Workflow` model) |
| NFR-4 | **No additional DB queries:** version is computed in memory before persistence; no extra HBase read needed |
| NFR-5 | **No new dependencies:** no new libraries or modules required |

---

## 5. API Contract

### Before

| Endpoint | Status | Response Body |
|---|---|---|
| `POST /nodeDefinition/{id}/publishNode/` | 200 OK | *(empty)* |
| `POST /workflowDefinition/{id}/publishWorkflow/` | 200 OK | *(empty)* |

### After

| Endpoint | Status | Response Body |
|---|---|---|
| `POST /nodeDefinition/{id}/publishNode/` | 200 OK | Full `NodeDefinition` JSON with `version` field set |
| `POST /workflowDefinition/{id}/publishWorkflow/` | 200 OK | Full `Workflow` JSON with `version` field set |

### Sample response (node):

```json
{
  "id": "DataValidationEngine",
  "name": "DataValidationEngine",
  "type": "PROCESSOR",
  "version": "14"
}
```

### Sample response (workflow):

```json
{
  "id": "order-flow",
  "startNode": "validate",
  "version": "3",
  "states": { ... }
}
```

---

## 6. Repo Integration Map

### 6.1 Files to modify (production)

| File | Module | Change |
|---|---|---|
| `api/src/main/java/com/flipkart/drift/api/service/builder/NodeDefinitionService.java` | `api` | Return type `void` → `NodeDefinition`; add two `return` statements |
| `api/src/main/java/com/flipkart/drift/api/service/builder/WorkflowDefinitionService.java` | `api` | Return type `void` → `Workflow`; add two `return` statements |
| `api/src/main/java/com/flipkart/drift/api/resources/NodeDefinitionResource.java` | `api` | Capture returned `NodeDefinition`; pass to `Response.ok()` |
| `api/src/main/java/com/flipkart/drift/api/resources/WorkflowDefinitionResource.java` | `api` | Capture returned `Workflow`; pass to `Response.ok()` |

### 6.2 Files to create (tests)

| File | Module | Package |
|---|---|---|
| `api/src/test/java/com/flipkart/drift/api/service/builder/NodeDefinitionServiceTest.java` | `api` | `com.flipkart.drift.api.service.builder` |
| `api/src/test/java/com/flipkart/drift/api/service/builder/WorkflowDefinitionServiceTest.java` | `api` | `com.flipkart.drift.api.service.builder` |

---

## 7. Error Handling

No changes to error handling. Both publish methods already wrap all exceptions in `ApiException` and throw, which the resource handler catches and converts to an HTTP 500 response. This path is unchanged.

---

## 8. Out of Scope

- Changes to the `POST /workflowDefinition/{id}/activate` endpoint (returns void, separate concern)
- Integration tests requiring live HBase/Redis connections
- Changes to request models, path parameters, or query parameters
- Changes to any other service methods (addNode, updateNode, addWorkflow, updateWorkflow, getNodeById, getWorkflowById)
