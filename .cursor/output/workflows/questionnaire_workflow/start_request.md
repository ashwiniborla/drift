# Start Request — questionnaire_workflow

## Overview
- **Workflow ID**: `questionnaire_workflow`
- **Purpose**: Started when a fake-delivery issue is raised. The workflow presents a questionnaire to the user, validates the response, creates a questionnaire incident on IMS, marks it solved, and optionally triggers a FakeWorkflow based on the selected option.

## Request payload / parameters

The workflow is started with the following data available in `_global`:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `issueDetail.issueId` | String | yes | The issue ID (e.g. `"6332"`) — determines flow direction (FORWARD/REVERSE) |
| `issueDetail.issueName` | String | yes | The issue name |
| `queueDetail.queueId` | String | yes | Queue ID (e.g. `"7726"`) |
| `queueDetail.queueName` | String | yes | Queue name (e.g. `"CS SelfServe"`) |
| `customer.customerId` | String | yes | Customer account ID |
| `orderDetails` | List | yes | Order details array with `orderId`, `orderItemId`, `orderItemUnitId` |
| `params.undeliveredType` | String | yes | Undelivered type (e.g. `"rfr"`, `"cnr"`) — used with flowDirection to select the correct questionnaire config |
| `threadContext.clientId` | String | no | Client ID (e.g. `"fk-dobby"`) |
| `threadContext.perfFlag` | String | no | Perf test flag (e.g. `"false"`) |
| `threadContext.userName` | String | no | Username |
| `threadContext.tenant` | String | no | Tenant (e.g. `"cs"`) |

## Sample request

```json
{
    "incidentId": "IN26030522110482791833",
    "workflowId": "WF-IN26030522110482791833",
    "threadContext": {
        "clientId": "fk-dobby",
        "perfFlag": "false",
        "userName": "fk-dobby",
        "tenant": "cs"
    },
    "params": {
        "undeliveredType": "rfr"
    },
    "issueDetail": {
        "issueId": "6332",
        "issueName": "6332"
    },
    "queueDetail": {
        "queueId": "7726",
        "queueName": "CS SelfServe"
    },
    "customer": {
        "customerId": "ACC4B3E2003E68B4B5CB99C0E56CB620504T"
    },
    "orderDetails": [
        {
            "orderId": "OD436834736032698100",
            "orderItemId": "436834736032698100",
            "orderItemUnitId": "436834736032698100000"
        }
    ]
}
```

## Notes
- `issueDetail.issueId` determines the flow direction: `1111` → FORWARD, `1112` → REVERSE (see `process_fake_workflow_details.groovy`).
- The questionnaire config is loaded from `questions_config.json` using the key `fake_<flowDirection>_<undeliveredType>` (e.g. `fake_forward_rfr`).
- `params.undeliveredType` is passed as a workflow parameter and used alongside `issueId` to select the correct questionnaire.
