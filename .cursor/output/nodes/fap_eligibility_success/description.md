# FAP Eligibility Success

## Overview
- **ID**: `fap_eligibility_success`
- **Type**: `SUCCESS`
- **Version**: `1`
- **Purpose**: Terminal node for the `fap_eligibility_check` sub-workflow. Marks successful completion of all eligibility and feasibility checks. When this workflow is inlined as a SUB_WORKFLOW, the flattener rewires this node's `nextNode` to continue to the parent workflow's next step (e.g., `branch_feasibility` or `branch_feasibility_smart`), making it a pass-through rather than a true terminal.

## Notes
- **Only acts as a true terminal** when `fap_eligibility_check` is executed standalone (not as a SUB_WORKFLOW).
- When inlined, `rewireChainEnd` in `SubWorkflowFlattener` sets `nextNode` and `end: false` on this node.
