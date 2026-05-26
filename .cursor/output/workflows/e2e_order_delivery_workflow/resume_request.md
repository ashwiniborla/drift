# Resume Request -- E2E Order Delivery Workflow

## Overview

This workflow uses a **SCHEDULER_WAIT** node (`e2e_scheduler_wait`) to pause for 3 hours between polling cycles. The scheduler service automatically resumes the workflow after the wait duration expires -- **no manual or external resume call is required**.

There are no INSTRUCTION nodes in this workflow, so there is no user-facing or callback-driven resume.

---

## Automatic Resume (Scheduler)

| Field | Value |
|-------|-------|
| **Waiting node** | `e2e_scheduler_wait` |
| **Wait type** | `SCHEDULER_WAIT` |
| **Duration** | 10800 seconds (3 hours) |
| **Execution mode** | `ASYNC` |
| **Resume trigger** | Automatic by Drift scheduler service |

When the scheduler fires, the workflow resumes from `e2e_scheduler_wait` and proceeds to `e2e_fetch_order_details` (Oxford API call), then through the use-case evaluation logic.

---

## Manual Resume (if needed for debugging / testing)

In testing or recovery scenarios, you can manually resume the workflow via the Drift resume API. Since the waiting node is a SCHEDULER_WAIT (not an INSTRUCTION), the resume request does not require a `viewResponse`.

**Endpoint:** `PUT /v3/workflow/resume/{workflowId}`

**Sample request body (manual resume):**

```json
{
    "workflowId": "WF-IN26031012530009765043"
}
```

**Minimal cURL example:**

```bash
curl -X PUT \
  'http://<drift-host>/v3/workflow/resume/WF-IN26031012530009765043' \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowId": "WF-IN26031012530009765043"
  }'
```

---

## Workflow Loop Behavior

The workflow loops through this cycle until the order reaches a terminal state:

```
e2e_scheduler_wait (3h)
    → e2e_fetch_order_details (Oxford)
    → e2e_use_case_branch (by issueId)
    → e2e_order_delivery_2111 (evaluate terminal conditions)
    → e2e_check_ended
        → ended=true  → update_incident_closed → e2e_workflow_success
        → ended=false → e2e_scheduler_wait (loop back, wait another 3h)
```

Each cycle:
1. Scheduler resumes the workflow automatically after 3 hours.
2. Oxford is called to fetch latest order-unit statuses.
3. The use-case groovy script checks terminal conditions.
4. If ended, the incident is closed (statusId=2) and the workflow completes.
5. If not ended, the workflow goes back to the scheduler wait for the next cycle.

---

## Notes

- The scheduler service creates the resume schedule internally when the workflow reaches the `e2e_scheduler_wait` node. No external setup is needed.
- If the workflow needs to be stopped before it reaches a terminal state, use the terminate API: `DELETE /v3/workflow/terminate/{workflowId}`.
- The `e2e_check_ended` BRANCH node routes back to `e2e_scheduler_wait` (not the start), so the incident status update (statusId=7) only happens once at workflow start.
