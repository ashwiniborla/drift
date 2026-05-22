# Drift — API Reference (Local Testing)

Base URL: `http://localhost:8000`
Required headers on every request:
```
X_TENANT_ID: test
X_CLIENT_ID: test-client
```

---

## Node Definition APIs

### Create node
```bash
curl -X POST http://localhost:8000/nodeDefinition/ \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{
    "id": "my-http-node",
    "name": "My HTTP Node",
    "type": "HTTP",
    "httpComponents": {
      "url": {"type":"STATIC","value":{"type":"STRING","data":"http://localhost:9998/retry-test"}},
      "headers": {"type":"STATIC","value":{"type":"STRING","data":"{}"}},
      "queryParams": {"type":"STATIC","value":{"type":"STRING","data":"{}"}},
      "body": {"type":"STATIC","value":{"type":"STRING","data":"{}"}},
      "method": "GET",
      "contentType": "application/json"
    },
    "transformerComponents": {
      "transformer": {"type":"SCRIPT","value":{"type":"STRING","data":"return _response"}}
    }
  }'
```

### Publish node (promotes SNAPSHOT → versioned)
```bash
curl -X POST http://localhost:8000/nodeDefinition/my-http-node/publishNode/ \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client'
```

### Get node definition
```bash
curl http://localhost:8000/nodeDefinition/test-retry-node?version=4 \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client'
```

> Version values: `1`, `2`, ... (published versions) · `SNAPSHOT` (draft) · `LATEST` (latest published)

---

## Workflow Definition APIs

### Create workflow definition
```bash
curl -X POST http://localhost:8000/workflowDefinition/ \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{
    "id": "my-workflow",
    "startNode": "step1",
    "states": {
      "step1": {
        "instanceName": "step1",
        "resourceId": "my-http-node",
        "resourceVersion": "1",
        "type": "NODE",
        "timeoutSeconds": 10,
        "retryConfig": {
          "maxAttempts": 3,
          "initialIntervalSeconds": 2,
          "maxIntervalSeconds": 30,
          "backoffCoefficient": 2.0
        },
        "end": true
      }
    }
  }'
```

### Publish workflow (promotes SNAPSHOT → versioned)
```bash
curl -X POST http://localhost:8000/workflowDefinition/my-workflow/publishWorkflow/ \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client'
```

### Get workflow definition
```bash
curl "http://localhost:8000/workflowDefinition/drift-retry-final?version=1" \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client'
```

### Update workflow definition (merges states additively)
```bash
curl -X PUT http://localhost:8000/workflowDefinition/ \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{ "id": "my-workflow", "startNode": "step1", "states": { ... } }'
```

> **Note:** PUT merges states additively — old states are never removed. Use a new workflow ID to start clean.

---

## Workflow Runtime APIs

### Start a workflow
```bash
curl -X POST http://localhost:8000/v3/workflow/start \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{
    "workflowId": "my-run-001",
    "params": {"workflowId": "drift-retry-final", "version": "1"},
    "issueDetail": {"issueId": "MY-ISSUE"},
    "threadContext": {"clientId": "test-client", "perfFlag": "false", "userName": "test", "tenant": "test"}
  }'
```

> `workflowId` in the body = unique run ID (must be unique per execution). `params.workflowId` = the WorkflowDefinition ID to run.

### Get workflow run state
```bash
curl http://localhost:8000/v3/workflow/my-run-001 \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client'
```

Response fields: `status` (RUNNING / COMPLETED / FAILED / WAITING), `currentNodeRef`, `view`, `errorMessage`

### Resume a waiting workflow
```bash
curl -X PUT http://localhost:8000/v3/workflow/resume/my-run-001 \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{
    "threadContext": {"clientId": "test-client", "perfFlag": "false", "userName": "test", "tenant": "test"},
    "resumeData": {}
  }'
```

### Terminate a workflow
```bash
curl -X DELETE http://localhost:8000/v3/workflow/terminate/my-run-001 \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{
    "reason": "manual termination"
  }'
```

---

## Test Workflows (already in HBase)

| Workflow ID | Version | Tests | Key node config |
|---|---|---|---|
| `drift-retry-final` | `1` | Retry — 3 attempts, flat backoff | `maxAttempts: 3`, `initialIntervalSeconds: 2` |
| `drift-timeout-v3` | `1` | Timeout — activity killed after 5s | `timeoutSeconds: 5` |

### Quick start: retry test
```bash
curl -X POST http://localhost:8000/v3/workflow/start \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{
    "workflowId": "retry-run-001",
    "params": {"workflowId": "drift-retry-final", "version": "1"},
    "issueDetail": {"issueId": "RETRY"},
    "threadContext": {"clientId": "test-client", "perfFlag": "false", "userName": "test", "tenant": "test"}
  }'
```

### Quick start: timeout test
```bash
curl -X POST http://localhost:8000/v3/workflow/start \
  -H 'Content-Type: application/json' \
  -H 'X_TENANT_ID: test' \
  -H 'X_CLIENT_ID: test-client' \
  -d '{
    "workflowId": "timeout-run-001",
    "params": {"workflowId": "drift-timeout-v3", "version": "1"},
    "issueDetail": {"issueId": "TIMEOUT"},
    "threadContext": {"clientId": "test-client", "perfFlag": "false", "userName": "test", "tenant": "test"}
  }'
```

> Both return HTTP 500 "Timeout waiting for workflow response" — expected. The API's Redis pub/sub times out because test workflows have no terminal SUCCESS/FAILURE node. Verify the actual execution on **Temporal UI → http://localhost:8080**.

---

## Health Checks

```bash
# API health
curl http://localhost:8001/healthcheck

# Worker health
curl http://localhost:7201/healthcheck

# Test server health (must be running separately)
curl http://localhost:9998/health
```

---

## Temporal UI

Open `http://localhost:8080` to see all workflow runs.

| What to look for | Where |
|---|---|
| Retry working | `httpExecute` activity started at `attempt: 3` |
| Timeout working | `ACTIVITY_TASK_TIMED_OUT`, `timeoutType: TIMEOUT_TYPE_START_TO_CLOSE` after 5s |
| Failed attempts visible | Workflow → History tab → `ACTIVITY_TASK_FAILED` events |
