# Start Request — Fake Workflow

## Overview
- **Workflow ID**: `fake_workflow`
- **Purpose**: Triggered when a fake/forward delivery case needs to be handled. Can be started directly (as a making workflow) or invoked as a subworkflow from the questionnaire workflow.

## Request payload / parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `workflowId` | String | yes | The workflow instance ID |
| `issueDetail` | Object | yes | `{ issueId, issueName }` — the issue for incident creation |
| `queueDetail` | Object | yes | `{ queueId, queueName }` — target queue |
| `customer` | Object | yes | `{ customerId }` — customer info |
| `orderDetails` | Array | yes | `[ { orderId, orderItemId, orderItemUnitId } ]` — order items |
| `params.override_issue_id` | String | no | When present (subworkflow), overrides issueDetail.issueId for incident creation; when absent (direct), original issueDetail is used |

## Sample request

```json
{
    "workflowId": "WF-IN26030510443947272055",
    "issueDetail": {
        "issueId": "1111",
        "issueName": "Token of apology"
    },
    "queueDetail": {
        "queueId": "Q001",
        "queueName": "Fake Delivery Queue"
    },
    "customer": {
        "customerId": "CUST12345"
    },
    "orderDetails": [
        {
            "orderId": "OD123456789",
            "orderItemId": "OI123",
            "orderItemUnitId": "OIU123"
        }
    ]
}
```

## Notes
- The workflow **start node** calls Oxford (`e2e_fetch_order_details`) with **`useCase: reporting`** and **`dataVariable: v2OrderData_imsv2_varadhi_client1_reporting`**; the response is stored under **`fetch_order_oxford`** for later GROOVY prep of the SA instruction screen.
- When used as a subworkflow, `process_fake_workflow_details.issueConfig.fakeIssueId` is available in context and is passed as `override_issue_id` to the incident-creation node.
- When used as a direct/making workflow, the `override_issue_id` path resolves to null and the original `issueDetail` from the start request is used.
