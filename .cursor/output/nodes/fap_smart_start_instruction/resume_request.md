# Resume Request — FAP Smart Action: Start Instruction

## Overview
- **Node ID**: `fap_smart_start_instruction`
- **Purpose**: Pauses the smart-action workflow after the V3 child workflow is registered on the incident. Resume triggers eligibility and feasibility checks.

## Resume scenarios

### Scenario 1: Operator confirms — proceed with workflow
- **Description**: The operator (or smart-action trigger) resumes this instruction to proceed with the phone number change.
- **Sample request / payload**:
  ```json
  {
    "selectedOptions": {}
  }
  ```
- **Outcome**: Workflow continues to `sub_eligibility` → eligibility/feasibility checks → phone collection.

### Scenario 2: No response / timeout
- **Description**: The instruction times out without a resume signal.
- **Sample request / payload**: N/A (timeout-driven)
- **Outcome**: Workflow may remain in WAITING state depending on runtime timeout configuration.

## Notes
- Contracts (inputOptions/layout) are TBD. The resume payload shape will be finalized when contracts are provided.
