# Start Request -- E2E Order Delivery Workflow

## Overview
- **Workflow ID**: `e2e_order_delivery_workflow`
- **Purpose**: Polls order-unit statuses every 3 hours via Oxford. Evaluates terminal conditions (delivered, cancelled, rejected, CPD breached, post-SLA) based on the issueId use case. Updates the incident to E2E waiting (statusId=7) at start, and closes it (statusId=2) when a terminal state is reached.

---

## Params (startWorkflowRequest.params)

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| **workflowId** | string | yes | Must be `e2e_order_delivery_workflow`. |
| **version** | string | no | Workflow version (e.g. `SNAPSHOT`). |
| **issueId** | string | yes | Determines which use-case logic to run. Currently supported: `2111` (Order Delivery). |
| **elixirEntityReferenceType** | string | no | Entity reference type (e.g. `TRACKING_ID`). Carried for context. |
| **elixirEntityReferenceId** | string | no | Entity reference ID (e.g. tracking ID value). Carried for context. |
| **elixirEntityType** | string | no | Entity type (e.g. `PHYSICAL`). Carried for context. |
| **elixirEntityFlow** | string | no | Entity flow (e.g. `FORWARD`). Carried for context. |

---

## Other context (top-level or threadContext)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| **incidentId** | string | yes | IMS incident ID. Used for status updates (E2E waiting, closed). |
| **orderDetails** | array | yes | Array of order units to track. Each item must have `orderId`, `orderItemId`, `orderItemUnitId`, `trackingId`. |
| **customer** | object | no | `{ customerId }`. |
| **issueDetail** | object | yes | `{ issueId, issueName }`. |
| **threadContext** | object | no | `{ clientId, tenant, userName, perfFlag }`. Used for IMS API headers. |

---

## Full sample start request

```json
{
    "issueDetail": {
        "issueId": "2111",
        "issueName": "Order Delivery"
    },
    "incidentId": "IN26031012530009765043",
    "customer": {
        "customerId": "ACC14018777512395717"
    },
    "orderDetails": [
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124201",
            "orderItemUnitId": "336920613620124201000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124209",
            "orderItemUnitId": "336920613620124209000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124208",
            "orderItemUnitId": "336920613620124208000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124210",
            "orderItemUnitId": "336920613620124210000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124206",
            "orderItemUnitId": "336920613620124206001",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124205",
            "orderItemUnitId": "336920613620124205000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124203",
            "orderItemUnitId": "336920613620124203000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124207",
            "orderItemUnitId": "336920613620124207000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124206",
            "orderItemUnitId": "336920613620124206000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124200",
            "orderItemUnitId": "336920613620124200000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124204",
            "orderItemUnitId": "336920613620124204000",
            "trackingId": "FMPC5831452742"
        },
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124202",
            "orderItemUnitId": "336920613620124202000",
            "trackingId": "FMPC5831452742"
        }
    ],
    "params": {
        "workflowId": "e2e_order_delivery_workflow",
        "version": "SNAPSHOT",
        "issueId": "2111",
        "elixirEntityReferenceType": "TRACKING_ID",
        "elixirEntityReferenceId": "FMPC5831452742",
        "elixirEntityType": "PHYSICAL",
        "elixirEntityFlow": "FORWARD"
    }
}
```

---

## Minimal start request

```json
{
    "issueDetail": {
        "issueId": "2111",
        "issueName": "Order Delivery"
    },
    "incidentId": "IN26031012530009765043",
    "orderDetails": [
        {
            "orderId": "OD336920613620124200",
            "orderItemId": "336920613620124201",
            "orderItemUnitId": "336920613620124201000",
            "trackingId": "FMPC5831452742"
        }
    ],
    "params": {
        "workflowId": "e2e_order_delivery_workflow",
        "version": "SNAPSHOT",
        "issueId": "2111"
    }
}
```

---

## Notes

- **orderDetails** must include every order item unit that the E2E workflow should track. The `orderItemUnitId` values are matched against Oxford's `oms3_order_data.units` map to evaluate terminal conditions.
- All units in `orderDetails` must belong to the same `orderId` since the Oxford call fetches data for a single order.
- The Oxford data variable defaults to `v2OrderData_cs_controller_client_check_default`. Override via the `e2e_fetch_order_details` node parameter `dataVariable` in the workflow state if your environment uses a different variable.
