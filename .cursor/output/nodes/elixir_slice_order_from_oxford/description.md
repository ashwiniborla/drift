# elixir_slice_order_from_oxford

- **ID**: `elixir_slice_order_from_oxford`
- **Type**: GROOVY
- **Version**: 1

## Purpose

Extracts Elixir-relevant order fields from the raw Oxford response stored at `_global.fetch_order_oxford`. Finds the unit matching `trackingId` from `_global.orderDetails[0]`, determines `itemType`, `postFulfillmentData`, and the `allowed` flag (EKL partner check). This replaces the transformation previously embedded in the `elixir_get_order_details` HTTP node.

## Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `dataVariable` | Oxford data-variable key used to navigate the response | `v2OrderData_imsv2_varadhi_client1_default` |

## Script

`worker/src/main/resources/scripts/create_elixir_ticket_workflow/get_post_fulfillment_data_by_tracking_id.groovy`

## Context access

| Key | Source |
|-----|--------|
| `_global.fetch_order_oxford` | Raw Oxford response from preceding `e2e_fetch_order_details` HTTP state |
| `_global.orderDetails[0].orderId` | Order ID for Oxford traversal |
| `_global.orderDetails[0].trackingId` | Tracking ID to match against units |
| `_global.nodeParameters.dataVariable` | Passed via node `parameters` |
| `_enum_store.postDeliveryIssues.eklPartners` | Partner list for `allowed` flag |

## Returns

Stored at `_global.elixir_get_order_details` (via `contextOverrideKey: elixir_get_order_details`):

```json
{
  "itemType": "PHYSICAL",
  "postFulfillmentData": { "trackingId": "...", "courierName": "...", ... },
  "allowed": true
}
```

## Expected previous nodes

`e2e_fetch_order_details` (with `contextOverrideKey: fetch_order_oxford`).

## Expected next nodes

`elixir_check_allowed` (reads `_global.elixir_get_order_details.allowed`).
