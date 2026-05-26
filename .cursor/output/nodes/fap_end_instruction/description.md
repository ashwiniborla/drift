# FAP Workflow: End Instruction

## Overview
- **ID**: `fap_end_instruction`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Terminal instruction node shown when the workflow ends without completing the phone update (eligibility failure, feasibility failure, or other error). Shared by both non-smart and smart parent workflows. In the non-smart parent, this is used as the `default_failure` state resource. In the smart parent, this follows `spawn_child_fap` (CHILD) in the `default_failure` chain.

## Script Details
- `layoutId`: static `"fap_end_screen"` (placeholder; final layout TBD)
- `workflowStatus`: returns `"CLOSED"`

## Usage in non-smart parent
The workflow state `default_failure` uses `fap_end_instruction` as its `resourceId`. All eligibility/feasibility branch failures route here.

## Usage in smart parent
`default_failure` state first runs `spawn_child_fap` (CHILD) which spawns the non-smart workflow asynchronously, then routes to `fap_end_instruction` as the terminal.

## Dependencies
- **Used in workflows**: `forward_address_phone_change` (via `default_failure`), `forward_address_phone_change_smart` (after `spawn_child_fap`)

## Notes
- Contracts (inputOptions/layout) are TBD.
- `end: true` in workflow state — workflow terminates after this instruction is displayed.
