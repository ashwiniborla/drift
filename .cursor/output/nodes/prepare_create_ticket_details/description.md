# Prepare Create Ticket Details

## Overview
- **ID**: `prepare_create_ticket_details`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Prepares all inputs required by the `push_to_varadhi_queue` generic node for the Elixir create-ticket use case. Also builds the `elxrTkt` map (status=REQUESTED) for the subsequent `update_incident_elixir_requested` node. Consolidates logic previously split between `build_create_elixir_ticket_body.groovy` (HTTP body) and `build_elixir_ticket_transformer.groovy` (elxrTkt object).

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `incidentId` | IMS incident ID; used as `external_id` in ticket body and as `groupId` for RESTBUS | `$.params.incidentId` |
| `issueId` | IMS issue ID; used to look up `issue_type` and `flowDirection` from `_enum_store.elixir.issueConfig` | `$.params.issueId` |
| `elixirEntityReferenceType` | Elixir entity reference type (e.g. `TRACKING_ID`) | `$.params.elixirEntityReferenceType` |
| `elixirEntityReferenceId` | Elixir entity reference ID (e.g. tracking ID from order details) | `$.elixir_get_order_details.postFulfillmentData.trackingId` |
| `elixirEntityType` | Elixir entity type (e.g. `PHYSICAL`) | `$.elixir_get_order_details.itemType` |
| `workflowId` | Current workflow instance ID; stored in `elxrTkt.workflowId` | `$.workflowId` |

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/create_elixir_ticket_workflow/prepare_create_ticket_details.groovy`
- **Context access**: `_global.nodeParameters.*`, `_enum_store.elixir.*`, `_enum_store.clients['elixir.ch.host']`, `_enum_store.elixir['publishQueue']`
- **Logic summary**:
  1. Validates all required parameters (throws `IllegalArgumentException` on missing values)
  2. Looks up `issue_type` and `flowDirection` from `_enum_store.elixir.issueConfig.<issueId>`
  3. Looks up `callbackUrl` from `_enum_store.elixir.callbackUrl`
  4. Builds the Elixir ticket request body
  5. Builds `extraHeaders` map (`X_CLIENT_ID`, `X_REQUEST_ID` with UUID, `X_TENANT_ID`)
  6. Resolves `httpUri` from `_enum_store.clients['elixir.ch.host']` + `/elixir/v1/tickets`
  7. Computes `messageId` as `incidentId + 4-digit random string`
  8. Resolves `queueName` from `_enum_store.elixir['publishQueue']`
  9. Builds `elxrTkt` map with `status=REQUESTED` for the incident update

## Output

Returns a single map stored in `_global.prepare_create_ticket_details`:

```
{
    // Inputs for push_to_varadhi_queue
    body         : { external_id, issue_type, entity_reference, entity_type, entity_flow, callback_url, message },
    extraHeaders : { X_CLIENT_ID, X_REQUEST_ID, X_TENANT_ID },
    httpUri      : <elixir.ch.host> + '/elixir/v1/tickets',
    method       : 'POST',
    groupId      : <incidentId>,
    messageId    : <incidentId + 4-digit random>,
    queueName    : <elixir publishQueue from enum store>,

    // Input for update_incident_elixir_requested
    elxrTkt      : { id, workflowId, status='REQUESTED', type, entity, createdAt, updatedAt }
}
```

## Dependencies
- **Reads from _global**: `_global.nodeParameters.*`
- **Reads from _enum_store**:
  - `_enum_store.elixir.issueConfig.<issueId>.issue` — issue type
  - `_enum_store.elixir.issueConfig.<issueId>.flowDirection` — entity flow
  - `_enum_store.elixir.callbackUrl` — callback URL for Elixir ticket
  - `_enum_store.clients['elixir.ch.host']` — base URL for Elixir API
  - `_enum_store.elixir['publishQueue']` — Varadhi queue name
- **Expected previous nodes**: `elixir_get_order_details` (for tracking ID and item type)

## Notes
- `messageId` is computed as `incidentId + random 4-digit string` (since `groupId` is present, per the agreed convention).
- This node replaces the dual responsibility of the old `elixir_create_ticket` HTTP node (body building + transformer).
- The `elxrTkt` is built at this stage (pre-queue-call) since the queue endpoint does not return any structured data to derive it from.
