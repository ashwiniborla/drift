# Elixir Action Branch

## Overview
- **ID**: `elixir_action_branch`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Routes the workflow based on the action from the elixir update callback. Handles error recovery, terminal (CLOSED), and non-terminal actions (OTHERS, ALT_PH_NUMBER_REQUIRED) by looping back to the instruction node.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Reads directly from `_global.prepare_elixir_action_update` | — |

## Branch Rules (evaluated in order)

| # | Condition | Next Node | Description |
|---|-----------|-----------|-------------|
| 1 | `error == true` | `elixir_waiting_for_updates` | Groovy prep failed; loop back without side effects |
| 2 | `action == 'CLOSED'` | `elixir_ticket_created_end` | Ticket closed; end workflow successfully |
| 3 | `action == 'ALT_PH_NUMBER_REQUIRED'` | `elixir_waiting_for_updates` | Alt phone needed; loop back (child workflow planned for future) |
| 4 | `action == 'OTHERS'` | `elixir_prepare_reach_out_instructions` | General update; prepare reach-out then loop back |
| default | — | `elixir_waiting_for_updates` | Unknown action; loop back safely |

## Dependencies
- **Reads from _global**: `prepare_elixir_action_update.error`, `prepare_elixir_action_update.action`
- **Expected previous nodes**: `update_incident_elixir_action` (HTTP, reuses update_incident)

## Notes
- Error rule is first to ensure failed preparations always loop back safely
- ALT_PH_NUMBER_REQUIRED currently loops back directly; will be changed to spawn a child workflow later
- OTHERS goes through `elixir_prepare_reach_out_instructions` (placeholder) before looping back
