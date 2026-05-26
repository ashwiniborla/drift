# Resume Request — Create Elixir Ticket Workflow

## Overview

The workflow enters a **resumable** state when it is waiting at the instruction node **elixir_waiting_for_response**. Elixir calls the IMS callback API after processing the create-ticket request; the callback is translated into a workflow resume request. The resume payload must include **selectedOptions** with the fields required by the downstream nodes (see table below).

- **Waiting node**: `elixir_waiting_for_response`
- **Resume payload key**: `selectedOptions` (or equivalent viewResponse structure your resume API uses)

---

## Fields required in resume payload (selectedOptions)

| Field       | Type   | Required | Description |
|------------|--------|----------|-------------|
| **status** | string | Yes      | `CREATED` or `REJECTED` |
| **ticket_id** | string | No    | Elixir ticket ID; `null` when status is `REJECTED` |
| **created_at** | string | Yes  | Timestamp from Elixir (e.g. `2026-02-19T13:10:44.963+0530`); used as `updatedAt` in incident elxrTkt update |
| **message** | string | No    | Optional message (e.g. rejection reason) |

---

## Scenario 1: Accepted (CREATED)

Use when Elixir has successfully created the ticket.

**Sample request body (accepted / CREATED):**

```json
{
  "workflowId": "WF-12345678-90ab-cdef-1234-567890abcdef",
  "nodeId": "elixir_waiting_for_response",
  "viewResponse": {
    "selectedOptions": {
      "status": "CREATED",
      "ticket_id": "TCKT-987654",
      "created_at": "2026-02-19T13:10:44.963+0530",
      "message": null
    }
  }
}
```

**Minimal payload (if your API only sends selectedOptions):**

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

**Outcome:** Workflow runs `prepare_elixir_response_update` → `update_incident_elixir_response` (partial elxrTkt with status, id, updatedAt) → `elixir_check_ticket_status` → **elixir_ticket_created_end** (SUCCESS).

---

## Scenario 2: Rejected (REJECTED)

Use when Elixir has rejected the ticket (e.g. duplicate, validation failure).

**Sample request body (rejected / REJECTED):**

```json
{
  "workflowId": "WF-12345678-90ab-cdef-1234-567890abcdef",
  "nodeId": "elixir_waiting_for_response",
  "viewResponse": {
    "selectedOptions": {
      "status": "REJECTED",
      "ticket_id": null,
      "created_at": "2026-02-19T13:10:44.963+0530",
      "message": "Duplicate ticket exists"
    }
  }
}
```

**Minimal payload (if your API only sends selectedOptions):**

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

**Outcome:** Workflow runs `prepare_elixir_response_update` → `update_incident_elixir_response` (partial elxrTkt with status, id, updatedAt) → `elixir_check_ticket_status` → **elixir_ticket_rejected_end** (SUCCESS, graceful end).

---

## Notes

- Callback from Elixir typically sends `action_type: ELIXIR_TICKET_UPDATE` and `action_context` with `ticket_id`, `external_id`, `status`, `created_at`. IMS/controller should map these into the resume request’s `selectedOptions` (e.g. `status` ← action_context.status, `ticket_id` ← action_context.ticket_id, `created_at` ← action_context.created_at).
- `created_at` from the callback is used as `updatedAt` in the partial elxrTkt update sent to IMS in both CREATED and REJECTED flows.
- The exact top-level keys of the resume request (e.g. `workflowId`, `nodeId`, `viewResponse`) depend on your Drift/IMS resume API; adjust the samples to match your contract while keeping `selectedOptions` content as above.
