# Start Request — Fetch Location Workflow

## Overview
- **Workflow ID**: `fetch_location`
- **Purpose**: Started (or inlined via subworkflow) when the system needs to evaluate whether a delivery location nudge is required for a customer. Typically triggered as part of `questionnaire_workflow` via the `collect_location` / `sub_fetch_location` state.

## Request payload / parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `workflowId` | String | yes | Unique workflow instance ID (e.g., `fetch_location`) |
| `version` | String | yes | Workflow version (e.g., `SNAPSHOT`) |
| `customer.customerId` | String | yes | Customer account ID — used as `accountId` in the AIS location evaluate API |
| `get_current_address` | Object | yes | Pre-populated contact/address object in context (from User Service). Must contain `id` (contactId) and `address` fields |
| `orderDetails[0].orderId` | String | yes | Order ID — used by `e2e_fetch_order_details` to fetch Oxford data |
| `orderDetails[0].trackingId` | String | yes | Tracking ID — used by Oxford fetch |

## Sample request

```json
{
  "workflowId": "fetch_location",
  "version": "SNAPSHOT",
  "customer": {
    "customerId": "ACC14018777512395717"
  },
  "orderDetails": [
    {
      "orderId": "OD436837524973794100",
      "orderItemId": "436837524973794100000",
      "orderItemUnitId": "436837524973794100000",
      "trackingId": "FMPC5792411756"
    }
  ],
  "get_current_address": {
    "id": "CNTCT1A296286EF69407F99D4D0502",
    "accountId": "ACC14018777512395717",
    "address": {
      "addressLine1": { "input": "201 inner space leafy Blocks" },
      "addressLine2": { "input": "Owners Court Layout, Habitat Celeste, Kasavanahalli" },
      "landmark": { "input": "Near shell petrol pump" },
      "city": { "input": "Bengaluru" },
      "pincode": { "input": "560035" },
      "state": { "input": "Karnataka" },
      "stateCode": { "input": "IN-KA" },
      "country": { "input": "IN" },
      "locationTypeTag": "Home"
    }
  }
}
```

## Notes
- When used as a subworkflow (`sub_fetch_location`), the parent workflow is responsible for having `get_current_address` populated in context before this workflow's states run.
- The `fetch_location_fetch_oxford` state overrides context so its output is accessible as `_global.fetch_order_oxford`.
- `defaultFailureNode` is `default_failure`; in subworkflow mode with `PROPAGATE` error handling, failures bubble to the parent's failure node.
