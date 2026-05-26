# Sub Workflow: Fetch Location

## Overview
- **ID**: `sub_fetch_location`
- **Type**: `SUB_WORKFLOW`
- **Version**: `1`
- **Purpose**: Inlines the `fetch_location` workflow into any parent workflow. Used in `questionnaire_workflow` to replace the stub `collect_location` GROOVY node.

## Configuration

| Field | Value | Notes |
|-------|-------|-------|
| `subWorkflowId` | `fetch_location` | |
| `subWorkflowVersion` | `SNAPSHOT` | |
| `includeFirstNode` | `true` | `fetch_location_fetch_oxford` is included |
| `includeLastNode` | `false` | `fetch_location_success` is excluded; parent's `nextNode` takes over |
| `errorHandlingStrategy` | `PROPAGATE` | Failures bubble up to the parent's `defaultFailureNode` |

## Flattening Behavior

With `includeLastNode: false`, the flattener rewires both convergence paths in `fetch_location`:
- `branch_nudge_required → fetch_location_success` (nudge=false path) is rewired to the parent's `nextNode`
- `show_location_instruction → fetch_location_success` (nudge=true path) is also rewired to the parent's `nextNode`

In `questionnaire_workflow`, the parent's `nextNode` for the `collect_location` state is `prepare_questionnaire_end_instructions`, so both paths continue there.

## Notes
- The state key in the parent workflow is kept as `collect_location` to avoid updating branch node references (`check_fake_workflow_required`, `check_location_screen_required`).
