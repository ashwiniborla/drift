# Resume Request — sa_fake_workflow_complete

## Overview
- **Node ID**: `sa_fake_workflow_complete`
- **Purpose**: Terminal completion screen for the SA fake workflow after incident notes are saved. The client renders `sa_workflow_complete_screen` with `sa.complete_workflow_component` and `workflow_success_instructions`.

## Resume scenarios

### Scenario 1: User completes the completion component
- **Description**: Agent or user acknowledges the success UI (e.g. dismiss / primary action as defined by `sa.complete_workflow_component` on the client).
- **Sample request / payload**: Resume payload includes `selectedOptions` / view response keys as required by the client contract for this layout (exact shape is client-defined).
- **Outcome**: Workflow is already terminal (`end: true`); resume may no-op or close the session per IMS/client rules.

### Scenario 2: No further input / session ends
- **Description**: User navigates away or the session ends without an explicit secondary action.
- **Sample request / payload**: May be empty or timeout-specific per controller.
- **Outcome**: Terminal state; no further workflow nodes.

### Scenario 3: Timeout / stale WAITING (if applicable)
- **Description**: If the client keeps the workflow in a waiting state briefly, a timeout could trigger a generic resume.
- **Sample request / payload**: Timeout or heartbeat per IMS conventions.
- **Outcome**: Terminal handling; no additional Drift nodes after this instruction in `fake_workflow`.

## Notes
- Top-level API fields such as `incidentId`, `workflowId`, and merged `view` in responses are often assembled by IMS from workflow context; this node supplies `view.inputOptions` and static layout/disposition/status as defined in `node.json`.
