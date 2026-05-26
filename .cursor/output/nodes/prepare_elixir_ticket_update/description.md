# prepare_elixir_ticket_update

## Overview

- **ID**: `prepare_elixir_ticket_update`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Builds Varadhi `push_to_varadhi` inputs for Elixir `POST /elixir/v1/tickets/{ticketId}/updates` with `action_type` `COMMUNICATION`. Reason/subreason text comes from `_enum_store.elixir.actionConfig[updateType].responseConfig`. If config is missing or incomplete, sets `sendElixirCommunicationUpdate: false` so a downstream BRANCH can skip the queue push.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `elixirTicketId` | Elixir ticket id (path segment and Varadhi `groupId`) | Workflow-dependent: e.g. `$.params.elixirTicketId`, or for **`forward_address_phone_change_smart`**: `$.get_incident_for_elixir.elixirTicketId` (from `get_incident` + `incident_extractor`) |
| `updateType` | Key under `elixir.actionConfig` (e.g. `ALT_PH_NUMBER_REQUIRED`) | literal or `$.params...` |
| `persona` | Optional; default `CX` | optional param |
| `persona_id` | Optional; often null | optional param |
| `asset_type` | Optional; often null | optional param |
| `id_type` | Optional; often null | optional param |
| `fieldsUpdatedV2` | Optional; when set, sent in the Elixir body as `fields_updated_v2`. Documented values (caller responsibility; script does not validate): `PINCODE`, `TEXT`, `TEXT_AND_PHONE_NUMBER`, `PHONE_NUMBER`, `SECONDARY_PHONE_NUMBER`. | optional param / literal |

## Script Details

### transformer (dynamic)

- **Location**: `worker/src/main/resources/scripts/create_elixir_ticket_workflow/prepare_elixir_ticket_update.groovy`
- **Context access**: `_global.nodeParameters`, `_enum_store.elixir.actionConfig`, `_enum_store.clients['elixir.ch.host']`, `_enum_store.elixir.publishQueue`
- **Logic summary**: Resolves `responseConfig` for `updateType`; on success returns body (COMMUNICATION + `action_context`, optional `fields_updated_v2` when `fieldsUpdatedV2` is set), `extraHeaders` (`X_CLIENT_ID`, `X_TENANT_ID`, `X_REQUEST_ID`), `httpUri`, `method`, `groupId` (= ticket id), `messageId` (= ticket id + 4 random digits), `queueName`, and `sendElixirCommunicationUpdate: true`. Otherwise returns `sendElixirCommunicationUpdate: false` and null push fields.

## Output

- **Returns**: Map with `sendElixirCommunicationUpdate` and, when true, fields consumed by `push_to_varadhi` (`body`, `extraHeaders`, `httpUri`, `method`, `groupId`, `messageId`, `queueName`).

## Dependencies

- **Reads from _global**: `nodeParameters`
- **Reads from _enum_store**: `elixir.actionConfig`, `clients['elixir.ch.host']`, `elixir.publishQueue`
- **Expected previous nodes**: Any that supply `elixirTicketId` via workflow `parameters` (e.g. after successful address update). For **forward_address_phone_change_smart**, the id is produced by state **`get_incident_for_elixir`** (HTTP `get_incident`), not start `params`.

## Notes

- When sending, requires `elixir.ch.host` and `elixir.publishQueue`; missing values throw (same expectation as create-ticket prepare).
- `updated_at` is formatted in `Asia/Kolkata` with offset compatible with `SimpleDateFormat` `Z` pattern.
