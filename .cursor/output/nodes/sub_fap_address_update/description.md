# Sub-workflow: FAP Address Update

## Overview
- **ID**: `sub_fap_address_update`
- **Type**: `SUB_WORKFLOW`
- **Version**: `1`
- **Purpose**: Inlines the `fap_address_update` workflow into the parent. Covers `create_new_address` (User Service POST /contacts) and `chore_confirm_address` (Chore confirm API). The flattener rewires `fap_address_update_success` to continue to the parent's `fap_success_instruction`.

## Configuration
- `subWorkflowId`: `fap_address_update`
- `subWorkflowVersion`: `SNAPSHOT`
- `includeFirstNode`: `true`
- `includeLastNode`: `true`
- `errorHandlingStrategy`: `PROPAGATE`

## Context dependency
`create_new_address` reads `_global['ask_alternate_phone:viewResponse'].selectedOptions` for `newContact` and `altContact`. This context key is always `ask_alternate_phone` because:
- In the non-smart parent: `ask_alternate_phone` state has `instanceName: "ask_alternate_phone"`
- In the smart parent: `ask_alternate_phone_smart` state has `contextOverrideKey: "ask_alternate_phone"`

## Used in workflows
- `forward_address_phone_change` (non-smart parent)
- `forward_address_phone_change_smart` (smart parent)
