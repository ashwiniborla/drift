# Resume Request — FAP Workflow: Success Instruction

## Overview
- **Node ID**: `fap_success_instruction`
- **Purpose**: Terminal success screen after phone number update. Displayed to operator/customer confirming the update. This is an end node (`end: true`) — no resume is expected after this instruction.

## Resume scenarios

### Scenario 1: Acknowledgement / close
- **Description**: Operator acknowledges the success message. No further workflow steps.
- **Sample request / payload**: N/A — `end: true`, workflow terminates.
- **Outcome**: Workflow is complete.

## Notes
- This is a terminal end instruction. Resume contracts are TBD and will be defined with the full UI contract.
