# Resume Request — Questionnaire End Instruction

## Overview
- **Node ID**: `questionnaire_end_instruction`
- **Purpose**: Terminal end screen showing a "request raised to delivery team" confirmation with a Submit button. The Submit button carries `metaData: { redirect: true }`, which signals the client to redirect without calling the resume workflow API. No actual resume is expected in normal flow.

## Resume scenarios

Although the UI does not call resume for this node (due to `redirect: true`), the scenarios are documented for completeness and edge-case handling.

### Scenario 1: User taps Submit (normal redirect flow)
- **Description**: User taps the Submit button. Because `redirect: true` is set in metaData, the client redirects directly without calling the resume API. The workflow state is already `end: true` so it terminates naturally.
- **Sample request / payload**: None — client does not call resume.
- **Outcome**: Workflow terminates. User is redirected by the client.

### Scenario 2: Resume called manually / programmatically (fallback)
- **Description**: If for any reason resume is called (e.g. testing, fallback client), the selected option would be `SUBMIT`.
- **Sample request / payload**:
  ```json
  {
    "workflowId": "WORKFLOW_INSTANCE_ID",
    "selectedOptions": {
      "submit_button": "SUBMIT"
    }
  }
  ```
- **Outcome**: Workflow state has `end: true` — execution terminates after this node regardless.

### Scenario 3: Timeout / no user action
- **Description**: User does not interact with the screen (session timeout or app close).
- **Sample request / payload**: No resume call made.
- **Outcome**: Workflow remains in WAITING state until it expires per the platform's workflow TTL policy.

## Notes
- The primary path is Scenario 1 — the `redirect: true` metaData is the key signal to the UI.
- This node does not set `disposition` or `workflowStatus` — these are omitted intentionally.
