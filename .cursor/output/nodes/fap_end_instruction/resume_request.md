# Resume Request — FAP Workflow: End Instruction

## Overview
- **Node ID**: `fap_end_instruction`
- **Purpose**: Terminal end screen shown when the phone update workflow cannot be completed (eligibility/feasibility failure). This is an end node — no resume is expected.

## Resume scenarios

### Scenario 1: Operator closes / acknowledges
- **Description**: Operator acknowledges the failure message. No further workflow steps.
- **Sample request / payload**: N/A — `end: true`, workflow terminates.
- **Outcome**: Workflow is complete (ended without phone update).

## Notes
- This is a terminal end instruction. Resume contracts are TBD.
