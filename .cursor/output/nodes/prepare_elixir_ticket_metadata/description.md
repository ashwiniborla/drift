# Prepare Elixir Ticket Metadata

## Overview
- **ID**: `prepare_elixir_ticket_metadata`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Resolves the canonical `elxrTkt` (Elixir ticket object) and the appropriate `notesText` for subsequent incident update steps. Acts as a single source of truth for these values regardless of whether the workflow is on a first-time ticket creation path or a re-request path (existing ticket found in `elixir_filter_incidents`).

## Parameters

None. Reads directly from `_global` context (no nodeParameters required).

## Script Details

### transformer (in-node)
- **Location**: embedded in `transformer.value.data`
- **Context access**:
  - `_global.prepare_create_ticket_details.elxrTkt` — present when a new ticket was just queued via `push_to_varadhi_queue`
  - `_global.elixir_filter_incidents.firstElixirDetails` — present when an existing Elixir ticket was found (re-request path)
- **Logic summary**:
  1. Reads `preparedElxrTkt` from `_global.prepare_create_ticket_details.elxrTkt`
  2. If non-null → `elxrTkt` = that value, `notesText` = `'Elixir ticket requested'` (new ticket path)
  3. If null → `elxrTkt` = `_global.elixir_filter_incidents.firstElixirDetails` (re-request path), `notesText` = `'Elixir ticket re requested'`

## Output

Returns a map stored in `_global.prepare_elixir_ticket_metadata`:

```
{
    elxrTkt  : <elxrTkt map>,
    notesText: 'Elixir ticket requested' | 'Elixir ticket re requested'
}
```

These values are consumed by:
- `update_incident_elixir_requested` — via workflow state parameters `elxrTkt` and `notesText`
- `prepare_elixir_action_update.groovy` — reads `_global.prepare_elixir_ticket_metadata.elxrTkt` as the base when building CLOSED elxrTkt

## Dependencies
- **Reads from _global**:
  - `_global.prepare_create_ticket_details.elxrTkt` (present on create-ticket path)
  - `_global.elixir_filter_incidents.firstElixirDetails` (present on re-request path)
- **Expected previous nodes**: `elixir_create_ticket` (push_to_varadhi_queue instance) — runs immediately after on the create-ticket path

## Notes
- No nodeParameters means no workflow state parameter mapping is needed.
- `_global` persists throughout the workflow run, so `prepare_elixir_action_update.groovy` (which runs much later on the updates path) can still safely read `_global.prepare_elixir_ticket_metadata.elxrTkt`.
- The `?: ` (Elvis operator) ensures graceful fallback — if `prepare_create_ticket_details.elxrTkt` is null/missing, `firstElixirDetails` is used without throwing.
