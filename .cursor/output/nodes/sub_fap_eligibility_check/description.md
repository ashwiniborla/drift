# Sub-workflow: FAP Eligibility Check

## Overview
- **ID**: `sub_fap_eligibility_check`
- **Type**: `SUB_WORKFLOW`
- **Version**: `1`
- **Purpose**: Inlines the `fap_eligibility_check` workflow into the parent. Covers Oxford order fetch, Minions eligibility check, Chore eligibility, User Service address fetch, and Chore feasibility. The flattener excludes `fap_eligibility_check`'s `default_failure` node (same name as parent's) so all failure branches delegate to the parent's `default_failure`.

## Configuration
- `subWorkflowId`: `fap_eligibility_check`
- `subWorkflowVersion`: `SNAPSHOT`
- `includeFirstNode`: `true`
- `includeLastNode`: `true`
- `errorHandlingStrategy`: `PROPAGATE`

## How flattening works
1. The flattener fetches `fap_eligibility_check` and inlines all its states except `default_failure` (name-matching exclusion).
2. `fap_eligibility_success` is identified as the sole terminal and rewired: `nextNode` → parent's `nextNode` (e.g., `branch_feasibility`), `end: false`.
3. All eligibility branch failures (`branch_minions_eligible`, `branch_chore_eligible`) still reference `default_failure`, which now resolves to the parent's `default_failure` state.

## Used in workflows
- `forward_address_phone_change` (non-smart parent)
- `forward_address_phone_change_smart` (smart parent)
