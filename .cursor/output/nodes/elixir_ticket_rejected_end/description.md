# Elixir Ticket Rejected End

## Overview
- **ID**: `elixir_ticket_rejected_end`
- **Type**: SUCCESS
- **Version**: `1`
- **Purpose**: Terminal node when Elixir callback reports status REJECTED (or any non-CREATED status). Workflow ends gracefully.

## Notes
- Reached from `elixir_check_ticket_status` default branch (when status != 'CREATED').
