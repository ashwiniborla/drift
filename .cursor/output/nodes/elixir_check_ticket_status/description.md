# Elixir Check Ticket Status

## Overview
- **ID**: `elixir_check_ticket_status`
- **Type**: BRANCH
- **Version**: `1`
- **Purpose**: Routes based on Elixir callback status from the instruction node viewResponse (CREATED vs REJECTED).

## Parameters

None. Reads from `_global`.

## Choices

| Rule | Condition | nextNode |
|------|-----------|----------|
| 1 | `_global.get('elixir_waiting_for_response:viewResponse')?.selectedOptions?.status == 'CREATED'` | `elixir_ticket_created_end` |
| default | No rule matched (REJECTED or any other status) | `elixir_ticket_rejected_end` |

## Output
- **Returns**: N/A (routing only).

## Dependencies
- **Reads from _global**: `elixir_waiting_for_response:viewResponse` (selectedOptions.status)
- **Expected previous node**: `update_incident_elixir_response` (after prepare_elixir_response_update)

## Notes
- CREATED: placeholder success node; may be replaced with an HTTP node later.
- REJECTED or unexpected status: graceful end via `elixir_ticket_rejected_end` (SUCCESS).
