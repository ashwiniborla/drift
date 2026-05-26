# Elixir Create Ticket

## Overview
- **ID**: `elixir_create_ticket`
- **Type**: HTTP
- **Version**: `1`
- **Purpose**: Fire-and-forget POST to Elixir create ticket API; post-execution transformer builds initial `elxrTkt` for IMS incident customFields (status=REQUESTED).

## Parameters

| Parameter | Description | Source (workflow) |
|-----------|-------------|-------------------|
| incidentId | External ID for the ticket (IMS incident ID) | `$.params.incidentId` |
| issueId | Issue config key (2111, 2112, 3267, 3234) | `$.params.issueId` |
| elixirEntityReferenceType | Entity reference type (e.g. TRACKING_ID, SHIPMENT_ID) | `$.params.elixirEntityReferenceType` |
| elixirEntityReferenceId | Entity reference ID | `$.params.elixirEntityReferenceId` |
| elixirEntityType | Entity type (e.g. PHYSICAL, DIGITAL, SERVICES) | `$.params.elixirEntityType` |
| elixirEntityFlow | Entity flow (e.g. FORWARD) | `$.params.elixirEntityFlow` |
| workflowId | Workflow ID for X_REQUEST_ID header | `$.id` |

## Script Details

### URL (SCRIPT)
- **Value**: `_enum_store.get('elixir.baseUrl') + '/elixir/v1/tickets'`
- **Enum key**: `elixir.baseUrl` (in lookup.properties: `global.elixir.baseUrl`)

### Headers (SCRIPT)
- `X_CLIENT_ID`: CX
- `X_TENANT_ID`: FLIPKART
- `X_REQUEST_ID`: workflow ID from `_global.nodeParameters.workflowId`
- `Content-Type`: application/json
- **No** `X_CLIENT_SECRET`

### Body (dynamic)
- **Location**: `src/main/resources/scripts/create_elixir_ticket_workflow/build_create_elixir_ticket_body.groovy`
- Validates all params non-null; looks up `issue_type` from `elixir.issueConfig.<issueId>.issue` and `callback_url` from `elixir.callbackUrl`; returns map with external_id, issue_type, entity_reference, entity_type, entity_flow, callback_url, message.

### Transformer (dynamic)
- **Location**: `src/main/resources/scripts/create_elixir_ticket_workflow/build_elixir_ticket_transformer.groovy`
- Builds initial `elxrTkt` object (id=null, workflowId, status=REQUESTED, type from enum, entity, createdAt, updatedAt) and returns `[ response: _response, elxrTkt: elxrTkt ]` so next node uses `$.elixir_create_ticket.elxrTkt`.

## Output
- **Returns**: Map with `response` (raw HTTP response) and `elxrTkt` (object for IMS customFields.elxrTkt). Stored in `_global.elixir_create_ticket` (instance name from workflow state).

## Dependencies
- **Reads from _global**: `nodeParameters.*` (all params above)
- **Reads _response**: HTTP response from Elixir API
- **Reads _enum_store**: `elixir.baseUrl`, `elixir.issueConfig.<issueId>.issue`, `elixir.callbackUrl`

## Notes
- Fire-and-forget: no response massaging; transformer output is used so update_incident_elixir_requested can pass `$.elixir_create_ticket.elxrTkt`.
