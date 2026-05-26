# FAP Workflow: Success Instruction

## Overview
- **ID**: `fap_success_instruction`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Terminal instruction node shown when the alternate phone number update completes successfully. Shared by both the non-smart (`forward_address_phone_change`) and smart (`forward_address_phone_change_smart`) parent workflows. End node (`end: true` in workflow state).

## Script Details
- `layoutId`: static `"fap_success_screen"` (placeholder; final layout TBD)
- `workflowStatus`: returns `"RESOLVED"`

## Dependencies
- **Must run after**: `chore_confirm_address` (via `sub_address_update` sub-workflow)
- **Used in workflows**: `forward_address_phone_change`, `forward_address_phone_change_smart`

## Notes
- Contracts (inputOptions/layout) are TBD.
- Since `end: true`, the workflow terminates after this instruction is displayed.
