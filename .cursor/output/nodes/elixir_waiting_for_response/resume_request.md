# Resume Request — Elixir Waiting For Response

## Overview
- **Node ID**: `elixir_waiting_for_response`
- **Purpose**: Workflow pauses at this instruction until the Elixir server calls the IMS callback API. The callback payload is used to resume the workflow; viewResponse contains status, ticket_id, created_at, and message.

## Resume scenarios

### Scenario 1: CREATED
- **Description**: Elixir ticket was created successfully. Callback includes ticket ID and timestamp.
- **Sample request / payload** (selectedOptions shape used by prepare_elixir_response_update and branch):

```json
{
  "selectedOptions": {
    "status": "CREATED",
    "ticket_id": "TCKT-987654",
    "created_at": "2026-02-19T13:10:44.963+0530",
    "message": null
  }
}
```

- **Outcome**: Branch routes to `elixir_ticket_created_end` (placeholder SUCCESS).

### Scenario 2: REJECTED
- **Description**: Elixir rejected the ticket (e.g. duplicate). ticket_id may be null; message may contain reason.
- **Sample request / payload**:

```json
{
  "selectedOptions": {
    "status": "REJECTED",
    "ticket_id": null,
    "created_at": "2026-02-19T13:10:44.963+0530",
    "message": "Duplicate ticket exists"
  }
}
```

- **Outcome**: Branch routes to `elixir_ticket_rejected_end` (SUCCESS, graceful end).

## Notes
- Callback API receives action_type `ELIXIR_TICKET_UPDATE` and action_context with ticket_id, external_id, status, created_at. IMS/controller maps this to the resume request with selectedOptions as above.
- `created_at` from callback is used as `updatedAt` in the partial elxrTkt update sent to IMS.
- While waiting, the instruction view is built dynamically: template `elixir_ticket_details`, tag `imsv2.info`, with `postFulfillmentData` from `_global.elixir_get_order_details.postFulfillmentData` (see `prepare_elixir_waiting_for_response_display`).
