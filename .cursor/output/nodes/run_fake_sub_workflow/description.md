# Run Fake Sub Workflow

## Overview
- **ID**: `run_fake_sub_workflow`
- **Type**: `SUB_WORKFLOW`
- **Version**: `1`
- **Purpose**: Inlines the `fake_workflow` into the `questionnaire_workflow` execution graph when `isFakeWorkflowRequired` is true. The fake workflow's terminal SUCCESS node is excluded (`includeLastNode: false`) so the parent workflow continues to the post-screens after the sub-workflow completes.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | No explicit parameters; relies on shared workflow context (`_global`) | — |

## Script Details

No scripts — SUB_WORKFLOW nodes do not contain executable scripts. The node references the `fake_workflow` definition by ID and version; Drift flattens its nodes inline at fetch time.

## Sub-Workflow Config

| Field | Value | Notes |
|-------|-------|-------|
| `subWorkflowId` | `fake_workflow` | The workflow to inline |
| `subWorkflowVersion` | `SNAPSHOT` | Uses the SNAPSHOT version |
| `includeFirstNode` | `true` | Start from `fake_workflow`'s start node (`fake_check_similarity_and_create_incident`) |
| `includeLastNode` | `false` | Excludes `fake_workflow_success` (terminal SUCCESS node); parent workflow continues after the last non-terminal step |
| `errorHandlingStrategy` | `PROPAGATE` | Failures inside the inlined fake workflow are handled by `questionnaire_workflow`'s `defaultFailureNode` |

## Output
- **Returns**: No direct node output. After inlining, the context from all fake_workflow node outputs is available in `_global` (e.g. `_global.fake_check_similarity_and_create_incident`, etc.)

## Dependencies
- **Triggered when**: `check_fake_workflow_required` branch evaluates `isFakeWorkflowRequired == true`
- **Sub-workflow must exist**: `fake_workflow` at version `SNAPSHOT` must be fetchable
- **Next node (parent)**: `check_alternate_phone_number_required`

## Notes
- Circular reference check: `fake_workflow` must not itself inline `questionnaire_workflow` (no circular dependency).
- If `fake_workflow`'s structure changes (e.g. last node before SUCCESS changes), the wiring into `check_alternate_phone_number_required` remains correct because `includeLastNode: false` always excludes the terminal node.
