# Start Request — prepare_current_address

## Overview
- **Workflow ID**: `prepare_current_address`
- **Purpose**: Not started directly. Used as an inlined sub-workflow via `sub_prepare_current_address` (SUB_WORKFLOW node) inside parent workflows.

## Prerequisites in `_global` at the point this sub-workflow runs

| Field | Type | Source | Description |
|-------|------|--------|-------------|
| `fetch_order_oxford` | Object | Parent `e2e_fetch_order_details` state | Raw Oxford resolved-variables response; needed by `extract_oxford_order_units` |
| `orderDetails` | Array | Workflow start request | Array of `{ orderId, orderItemId, orderItemUnitId, trackingId }`; needed to resolve `targetUnitIds` |

## Parameters inherited by states

| State | Parameter | Expression | Description |
|-------|-----------|------------|-------------|
| `extract_oxford_order_units` | `dataVariable` | `"v2OrderData_imsv2_varadhi_client1_default"` | Oxford variable key (hardcoded default) |
| `get_current_address` | `userSvcAuthToken` | `$.get_user_svc_auth_token.access_token` | Token from preceding `get_user_svc_auth_token` state |

## Notes
- This workflow has no external start request; it is wired by the parent via `sub_prepare_current_address` SUB_WORKFLOW node.
- `includeLastNode: false` in `sub_prepare_current_address` means `prepare_current_address_success` is excluded from the flattened parent graph; the parent continues with the node after the sub-workflow entry.
