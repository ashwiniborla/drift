# Prepare Elixir Action Update

## Overview
- **ID**: `prepare_elixir_action_update`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Prepares the incident update body based on the action received from elixir. Handles CLOSED (elxrTkt update), OTHERS (thread with reason details), and actions that have `_enum_store.elixir.actionConfig[action].smartActionConfig` (thread via `elixir.action.threadText.<action>` and child workflow name/version from that config). All logic is wrapped in try/catch for resilience.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Reads directly from `_global` and `_enum_store` | — |

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/create_elixir_ticket_workflow/prepare_elixir_action_update.groovy`
- **Context access**:
  - `_global.get('elixir_waiting_for_updates:viewResponse')?.selectedOptions` — action and context from instruction
  - `_global.elixir_create_ticket?.elxrTkt` — base elxrTkt from ticket creation
  - `_enum_store` — thread text lookups
- **Logic summary**:
  - **CLOSED**: builds full elxrTkt with status=CLOSED, updatedAt=now
  - **Action with `smartActionConfig`**: looks up `elixir.action.threadText.<action>` from enum store, falls back to formatted reason/subreason text
  - **Others type**: looks up `elixir.updates.threadText.<reasonCode>.<subreasonCode>` from enum store, falls back to formatted reason/subreason text
  - Builds thread with threadEntryType id=30, createdByUser=persona
  - Wraps all logic in try/catch; on error returns error flag

## Output
- **Returns**: Map with keys:
  - `action` (String) — the action value from the callback
  - `elxrTkt` (Map or null) — full elxrTkt object for CLOSED case
  - `threads` (List or null) — thread request list for OTHERS/ALT_PH_NUMBER_REQUIRED
  - `error` (Boolean) — true if an exception occurred during preparation

## Dependencies
- **Reads from _global**: `elixir_waiting_for_updates:viewResponse`, `elixir_create_ticket.elxrTkt`
- **Expected previous nodes**: `elixir_waiting_for_updates` (INSTRUCTION)

## Notes
- The action categorization map is extensible: add new action names to classify them as 'action' type vs 'others' type
- Default fallback text format is the same for both action and others types
- On error, the branch node routes back to the instruction node (no side effects)
