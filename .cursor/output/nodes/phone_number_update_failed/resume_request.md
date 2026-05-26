# Resume Request — Phone number update failed

## Overview

- **Node ID**: `phone_number_update_failed`
- **Purpose**: User acknowledges the message by tapping **Okay**; the client sends a resume payload with the selected option so the workflow can continue.

## Resume scenarios

### Scenario 1: User taps Okay

- **Description**: User selects the **Okay** button (`phone_number_updated_failure_okay_button`).
- **Sample request / payload**: Resume body includes selected options for the instruction, e.g. widget id → chosen value:

```json
{
  "selectedOptions": {
    "phone_number_updated_failure_okay_button": "okay"
  }
}
```

(Exact envelope field names depend on IMS/Drift resume API version; often nested under `viewResponse` / instance-scoped keys in `_global`.)

- **Outcome**: Workflow routes to the `nextNode` configured for this state after resume (e.g. success/end or next step — set when the node is added to a workflow).

### Scenario 2: User abandons / closes without Okay

- **Description**: User leaves without submitting; behavior depends on product (timeout, no resume, or stale session).
- **Sample request / payload**: No resume, or empty/partial payload if the client sends a cancel.
- **Outcome**: Workflow may remain **WAITING**, or a separate timeout/cancel path may be configured at workflow level.

### Scenario 3: Duplicate or late resume

- **Description**: Client retries resume after the workflow has already moved on.
- **Sample request / payload**: Same as Scenario 1.
- **Outcome**: Platform may reject as invalid state or no-op; handled by IMS/workflow guards.

## Notes

- Branching after resume should key off `selectedOptions` for `phone_number_updated_failure_okay_button` (value `okay`) when you add this node to a workflow with a BRANCH or next-state rules.
