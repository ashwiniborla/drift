# Prepare Elixir Response Update

## Overview
- **ID**: `prepare_elixir_response_update`
- **Type**: GROOVY
- **Version**: `1`
- **Purpose**: Builds the **full** `elxrTkt` object for the second incident update (after callback). Sending only a few fields would cause the backend to replace the entire customFields.elxrTkt and drop entity/workflowId/type, so we merge the initial elxrTkt with callback fields and return the complete object.

## Parameters

None. Reads from `_global`.

## Script Details

### Transformer (dynamic)
- **Location**: `src/main/resources/scripts/create_elixir_ticket_workflow/prepare_elixir_response_update.groovy`
- **From initial elxrTkt** (`_global.elixir_create_ticket.elxrTkt`): `workflowId`, `type`, `entity` (referenceType, referenceId, type), `createdAt`.
- **From callback viewResponse** (`elixir_waiting_for_response:viewResponse.selectedOptions`): `id` ← ticket_id, `status`, `updatedAt` ← created_at.
- Returns the **full** map: `id`, `workflowId`, `status`, `type`, `entity`, `createdAt`, `updatedAt`.

## Output
- **Returns**: Full elxrTkt map for IMS incident customFields.elxrTkt so the entire object is updated (entity details preserved). Stored in `_global.prepare_elixir_response_update`.

## Dependencies
- **Reads from _global**: `elixir_create_ticket.elxrTkt` (initial full object), `elixir_waiting_for_response:viewResponse` (selectedOptions: status, ticket_id, created_at, message)
- **Expected previous nodes**: `elixir_create_ticket`, `elixir_waiting_for_response` (resumed by callback)

## Notes
- Backend updates replace the whole customFields.elxrTkt when only a subset of fields is sent; therefore we always send the full object including entity details.
- Used by update_incident_elixir_response state with `elxrTkt: $.prepare_elixir_response_update`.
