# Elixir Waiting for Updates

## Overview
- **ID**: `elixir_waiting_for_updates`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Pauses the workflow in a WAITING state after the elixir ticket has been accepted (CREATED). Waits for an external callback providing an action and optional context details from the elixir system.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | No runtime parameters; all data comes via the resume callback | — |

## Input Options

| Option ID | Description |
|-----------|-------------|
| `elixir_update_placeholder` | Static text indicating the workflow is waiting for an elixir update |
| `action` | The action type from the elixir callback: `ALT_PH_NUMBER_REQUIRED`, `OTHERS`, or `CLOSED` |
| `context` | Plain object containing: `reasonCode`, `subreasonCode`, `reasonText`, `subReasonText`, `persona` |

## Output
- **viewResponse key**: `elixir_waiting_for_updates:viewResponse`
- **selectedOptions**: Contains `action` (string) and `context` (map with reason/persona fields)

## Dependencies
- **Reads from _global**: Nothing directly; this node waits for external input
- **Expected previous nodes**: `elixir_check_ticket_status` (CREATED branch) or loop-back from `elixir_action_branch` / `elixir_prepare_reach_out_instructions`

## Notes
- This instruction node is the loop-back target for non-terminal actions (OTHERS, ALT_PH_NUMBER_REQUIRED, errors)
- The resume payload must include `action` and `context` fields
- `context` fields are optional for CLOSED action but required for OTHERS and ALT_PH_NUMBER_REQUIRED
