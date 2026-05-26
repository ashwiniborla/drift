# Resume Request — Show Location Instruction

## Overview
- **Node ID**: `show_location_instruction`
- **Purpose**: Displays the delivery location confirmation screen. The user can tap the "Submit" button (which redirects to the location sharing deepLink). No resume is expected because `workflowStatus` is `COMPLETED` — but a resume may arrive if the parent workflow is still technically active.

## Resume Scenarios

### Scenario 1: User taps Submit (deeplink redirect)
- **Description**: The user taps the "Submit" button on the location screen. The UI opens the deepLink URL for location sharing. The workflow status is already COMPLETED so no further server-side action is needed.
- **Sample request / payload**:
  ```json
  {
    "workflowId": "WF-IN<INCIDENT_ID>",
    "nodeId": "show_location_instruction",
    "response": {
      "selectedOptions": {
        "submit_location_button": "SUBMIT"
      }
    }
  }
  ```
- **Outcome**: Workflow proceeds to `fetch_location_success` (terminal SUCCESS node).

### Scenario 2: No user interaction / timeout
- **Description**: The user does not interact with the screen (e.g., app is closed, session expires). No resume signal arrives.
- **Sample request / payload**: *(none — workflow remains in WAITING state until timeout)*
- **Outcome**: Workflow remains paused; upstream orchestrator may clean it up via TTL or manual intervention.

## Notes
- Because `workflowStatus` is set to `COMPLETED` in the instruction response, the UI client treats the workflow as done and does not prompt the user to resume.
- The deepLink URL is populated from `_global.evaluate_location.nudge.deepLink` by the `prepare_location_instruction` node.
- When used inside `questionnaire_workflow` via the `sub_fetch_location` subworkflow, the parent workflow's flattener wires this node's completion to `prepare_questionnaire_end_instructions`.
