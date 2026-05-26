# prepare_current_address

## Overview
- **ID**: `prepare_current_address`
- **Version**: `1`
- **Start node**: `extract_oxford_order_units`
- **Default failure node**: `default_failure`
- **Comment**: Shared prep sub-workflow that populates `_global.oxford_unit_details`, `_global.get_user_svc_auth_token`, and `_global.get_current_address` so downstream flows (fetch_location, fap_eligibility_check) have the address and order units they need.

## States

State keys are intentionally prefixed with `pca_` to avoid duplicate-state-key errors when this sub-workflow is inlined in the same root graph alongside `fap_eligibility_check` (which still contains states with keys `get_user_svc_auth_token` and `get_current_address`). The `contextOverrideKey` on each state ensures output is stored at the canonical `_global` key so downstream scripts and parameter paths work unchanged.

| State key | Resource ID | `_global` output key (`contextOverrideKey`) | Next Node | Terminal | Parameters |
|-----------|-------------|---------------------------------------------|-----------|----------|------------|
| `pca_extract_oxford_order_units` | `extract_oxford_order_units` | `oxford_unit_details` | `pca_get_user_svc_auth_token` | no | `dataVariable` |
| `pca_get_user_svc_auth_token` | `get_user_svc_auth_token` | `get_user_svc_auth_token` | `pca_get_current_address` | no | — |
| `pca_get_current_address` | `get_current_address` | `get_current_address` | `prepare_current_address_success` | no | `userSvcAuthToken` |
| `prepare_current_address_success` | `prepare_current_address_success` | — | — | yes | — |
| `default_failure` | `default_failure` | — | — | yes | — |

## Flow

`pca_extract_oxford_order_units` → `pca_get_user_svc_auth_token` → `pca_get_current_address` → `prepare_current_address_success` (terminal, excluded by `includeLastNode: false` in the `sub_prepare_current_address` node).

## Prerequisite

`_global.fetch_order_oxford` must be present before this workflow runs. It is produced by an `e2e_fetch_order_details` state with `contextOverrideKey: fetch_order_oxford` in the parent workflow (e.g. `questionnaire_workflow_fetch_oxford` in `questionnaire_workflow`).

## Consumed by

Used via the `sub_prepare_current_address` SUB_WORKFLOW node. Parent workflows that must inline it:
- `questionnaire_workflow` — after `questionnaire_workflow_fetch_oxford`
- `forward_address_phone_change` — after `fap_eligibility_fetch_oxford`, before `sub_eligibility`
- `forward_address_phone_change_smart` — after `fap_eligibility_fetch_oxford` / `register_child_workflow_update_incident`, before `sub_eligibility`

## Notes
- After slim-down of `fap_eligibility_check`, `get_user_svc_auth_token` and `get_current_address` are no longer produced inside eligibility. This workflow is the sole source of those keys.
- `extract_oxford_order_units` stores output under `contextOverrideKey: oxford_unit_details` so `_global.oxford_unit_details` is set (same key as the old `fap_eligibility_extract_oxford_units` state in `fap_eligibility_check`).
