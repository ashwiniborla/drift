# extract_oxford_order_units

- **ID**: `extract_oxford_order_units`
- **Type**: GROOVY
- **Version**: 1

## Purpose

Transforms the raw Oxford resolved-variables response (stored at `_global.fetch_order_oxford` by the preceding `e2e_fetch_order_details` HTTP state) into a structured map containing the full `units` map and the list of `targetUnitIds` from the workflow input. Used immediately after the fetch step in workflows that need unit-level data (FAP eligibility, E2E order delivery).

## Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `dataVariable` | Oxford data-variable key used to navigate the response | `v2OrderData_imsv2_varadhi_client1_default` |

## Script

`worker/src/main/resources/scripts/shared/extract_oxford_order_units.groovy`

## Context access

| Key | Source |
|-----|--------|
| `_global.fetch_order_oxford` | Raw Oxford response from preceding `e2e_fetch_order_details` HTTP state |
| `_global.orderDetails` | Array of `{orderId, orderItemId, orderItemUnitId, trackingId}` from workflow start |
| `_global.nodeParameters.dataVariable` | Passed via node `parameters` |

## Returns

Stored at `_global.<instanceName>` (typically overridden to `fetch_order_oxford` via `contextOverrideKey`):

```json
{
  "units": { "<unitId>": { "status": "...", "chores": [...], "postFulfillmentData": {...}, ... } },
  "targetUnitIds": ["<orderItemUnitId1>", ...]
}
```

## Expected previous nodes

`e2e_fetch_order_details` (must run with `contextOverrideKey: fetch_order_oxford` so raw response is at `_global.fetch_order_oxford`).

## Expected next nodes

`check_minions_eligibility` (FAP path) or `e2e_use_case_branch` / `e2e_fake_forward` (E2E path).
