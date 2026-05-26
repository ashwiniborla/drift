# FAP Smart Action: Start Instruction

## Overview
- **ID**: `fap_smart_start_instruction`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Blank/waiting instruction node in the smart-action workflow. Pauses execution after the `update_incident` call (which registers this workflow as a V3 child on the incident). The operator/system resumes this node to trigger the eligibility and feasibility checks. This node acts as the handoff point between the IMS smart-action registration and the actual phone change logic.

## Script Details
- `layoutId`: static `"fap_smart_start_screen"` (placeholder; final layout TBD)
- `workflowStatus`: returns `"IN_PROGRESS"`

## Output
- No meaningful output stored in `_global`. Acts as a pause point only.

## Dependencies
- **Must run after**: `update_incident` (which adds the V3 child workflow to the incident)
- **Used in workflow**: `forward_address_phone_change_smart`

## Notes
- Contracts (inputOptions/layout) are deferred and will be provided separately.
- When resumed, the workflow continues to `sub_eligibility` (which inlines `fap_eligibility_check`).
