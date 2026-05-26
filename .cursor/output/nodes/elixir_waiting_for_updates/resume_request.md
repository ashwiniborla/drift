# Resume Request — Elixir Waiting for Updates

## Overview
- **Node ID**: `elixir_waiting_for_updates`
- **Purpose**: Resumes the workflow after an elixir update is received. The callback provides an action type and optional context details that drive incident updates and workflow routing.

## Resume Scenarios

### Scenario 1: CLOSED
- **Description**: The elixir ticket has been closed. The workflow will update the incident's elxrTkt custom field to CLOSED status and then end the workflow successfully.
- **Sample request / payload**:
```json
{
    "action": "CLOSED",
    "context": {}
}
```
- **Outcome**: Groovy node builds elxrTkt with status=CLOSED, update_incident is called, branch routes to `elixir_ticket_created_end` (SUCCESS).

### Scenario 2: OTHERS
- **Description**: A general update from elixir with reason details. The workflow will add a thread (type 30) to the incident with the reason information and loop back to wait for more updates.
- **Sample request / payload**:
```json
{
    "action": "OTHERS",
    "context": {
        "reasonCode": "DELAYED_PICKUP",
        "subreasonCode": "WEATHER",
        "reasonText": "Delayed Pickup",
        "subReasonText": "Weather conditions",
        "persona": "elixir_agent"
    }
}
```
- **Outcome**: Groovy node builds a thread with text from enum store (key `elixir.updates.threadText.<reasonCode>.<subreasonCode>`) or fallback formatted text. update_incident adds the thread. Branch routes back to `elixir_waiting_for_updates`.

### Scenario 3: ALT_PH_NUMBER_REQUIRED
- **Description**: Elixir requires an alternate phone number from the customer. The workflow will add a thread (type 30) to the incident and loop back to wait for more updates (child workflow spawn planned for future).
- **Sample request / payload**:
```json
{
    "action": "ALT_PH_NUMBER_REQUIRED",
    "context": {
        "reasonCode": "ALT_PH_NUMBER",
        "subreasonCode": "UNREACHABLE",
        "reasonText": "Alternate Phone Number",
        "subReasonText": "Customer unreachable",
        "persona": "elixir_system"
    }
}
```
- **Outcome**: Groovy node builds a thread with text from enum store (key `elixir.action.threadText.ALT_PH_NUMBER_REQUIRED`) or fallback formatted text. update_incident adds the thread. Branch routes back to `elixir_waiting_for_updates`.

## Notes
- The `context` object can be empty for CLOSED action
- For OTHERS and ALT_PH_NUMBER_REQUIRED, the `persona` field is used as `createdByUser` in the thread
- If the groovy preparation fails (try/catch), the branch routes back to this instruction with no incident update side effects
