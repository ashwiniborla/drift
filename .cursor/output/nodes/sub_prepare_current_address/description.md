# sub_prepare_current_address

## Overview
- **ID**: `sub_prepare_current_address`
- **Type**: `SUB_WORKFLOW`
- **Version**: `1`
- **Purpose**: Inlines the `prepare_current_address` workflow into the parent. Populates `_global.oxford_unit_details`, `_global.get_user_svc_auth_token`, and `_global.get_current_address` before eligibility checks or `fetch_location` run.

## Configuration
- **subWorkflowId**: `prepare_current_address`
- **subWorkflowVersion**: `SNAPSHOT`
- **includeFirstNode**: `true` — include `extract_oxford_order_units` as the first inlined node
- **includeLastNode**: `false` — exclude the terminal `prepare_current_address_success` node; parent continues directly after `get_current_address`
- **errorHandlingStrategy**: `PROPAGATE` — failures route to the root workflow's `default_failure`

## Context keys produced (after flattening)

The state keys inlined into the parent are `pca_extract_oxford_order_units`, `pca_get_user_svc_auth_token`, and `pca_get_current_address`. Each has a `contextOverrideKey` that routes output to the canonical `_global` key, so existing scripts and parameter paths work unchanged.

| `_global` output key | Inlined state key | Description |
|---------------------|-------------------|-------------|
| `_global.oxford_unit_details` | `pca_extract_oxford_order_units` | Oxford units map + targetUnitIds |
| `_global.get_user_svc_auth_token` | `pca_get_user_svc_auth_token` | User Service OAuth token response |
| `_global.get_current_address` | `pca_get_current_address` | Current delivery address from User Service |

## Usage in parent workflows
Add a state entry pointing to `sub_prepare_current_address` resource, positioned after the Oxford raw fetch and before eligibility / `collect_location`:

```json
"prepare_current_address": {
    "instanceName": "prepare_current_address",
    "resourceId": "sub_prepare_current_address",
    "resourceVersion": "SNAPSHOT",
    "type": "NODE",
    "parameters": {},
    "nextNode": "<next_state>",
    "end": false
}
```
