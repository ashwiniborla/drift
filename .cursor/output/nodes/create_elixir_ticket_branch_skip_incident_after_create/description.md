# create_elixir_ticket_branch_skip_incident_after_create

## Overview
- **ID**: `create_elixir_ticket_branch_skip_incident_after_create`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: After `elixir_create_ticket`, skips `update_incident_elixir_requested` when `params.ignore_incident_update` is boolean `false` (caller updates incident externally). Otherwise runs the normal IMS update with elixir-requested fields.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Reads `ignore_incident_update` from workflow context | `_global.params.ignore_incident_update` |

## Script Details

### Choice 1 — skip incident update
- **Rule**: `_global.params?.ignore_incident_update == false`
- **Next node**: `elixir_waiting_for_response`

### Choice 2 — normal flow (value not false)
- **Rule**: `_global.params?.ignore_incident_update != false`
- **Next node**: `update_incident_elixir_requested`

### Default node
- `update_incident_elixir_requested` — fallback if neither rule matches (should not occur for boolean/null params).

## Output
- No direct output (BRANCH nodes produce routing only).

## Dependencies
- **Reads from _global**: `_global.params.ignore_incident_update`
- **Expected previous nodes**: `elixir_create_ticket`

## Notes
- Rules are evaluated in order; the first match wins (`== false` before `!= false`).
- Uses the same flag as `create_elixir_ticket_branch_skip_incident_entry` so entry and post-create skips stay consistent.
