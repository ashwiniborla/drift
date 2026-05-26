# create_elixir_ticket_branch_skip_incident_entry

## Overview
- **ID**: `create_elixir_ticket_branch_skip_incident_entry`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: At workflow start, skips `register_elixir_child_workflow` when the caller sets `params.ignore_incident_update` to boolean `false` (caller handles child registration externally). Otherwise runs the normal registration step.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Reads `ignore_incident_update` from workflow context | `_global.params.ignore_incident_update` |

## Script Details

### Choice 1 — skip register
- **Rule**: `_global.params?.ignore_incident_update == false`
- **Next node**: `elixir_fetch_order_oxford`

### Choice 2 — normal flow (value not false)
- **Rule**: `_global.params?.ignore_incident_update != false`
- **Next node**: `register_elixir_child_workflow`

### Default node
- `register_elixir_child_workflow` — fallback if neither rule matches (should not occur for boolean/null params).

## Output
- No direct output (BRANCH nodes produce routing only).

## Dependencies
- **Reads from _global**: `_global.params.ignore_incident_update`
- **Expected previous nodes**: Workflow entry (no prior node output required).

## Notes
- Rules are evaluated in order; the first match wins (`== false` before `!= false`).
