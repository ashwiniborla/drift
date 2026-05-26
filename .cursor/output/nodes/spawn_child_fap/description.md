# Spawn Child: Forward Address Phone Change

## Overview
- **ID**: `spawn_child_fap`
- **Type**: `CHILD`
- **Version**: `1`
- **Purpose**: Spawns the non-smart `forward_address_phone_change` workflow as an ASYNC Temporal child. Used in the smart parent's `default_failure` state — when eligibility or feasibility fails in the smart workflow, this node launches the non-smart workflow as a child (so the user can still complete the phone change via the standard flow) and then the smart parent continues to `fap_end_instruction`.

## Configuration
- `childWorkflowId`: `forward_address_phone_change`
- `childWorkflowVersion`: `SNAPSHOT`
- `executionMode`: `ASYNC` — smart parent does NOT wait for the child to complete

## Safety
- Spawns the **non-smart** `forward_address_phone_change`, not the smart workflow itself. No recursion risk — the non-smart workflow's `default_failure` shows `fap_end_instruction` (no child spawning).

## Dependencies
- **Used in workflow**: `forward_address_phone_change_smart` (as `default_failure` state resource)
- **nextNode in workflow state**: `fap_end_instruction`
