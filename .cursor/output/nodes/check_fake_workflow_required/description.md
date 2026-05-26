# check_fake_workflow_required

## Overview
- **ID**: `check_fake_workflow_required`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Checks whether a FakeWorkflow needs to be executed based on the `isFakeWorkflowRequired` flag from the user's selected questionnaire option.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Reads from `_global` directly | `_global.validate_questionnaire_response.isFakeWorkflowRequired` |

## Branch Logic

| Rule | Condition | Next Node |
|------|-----------|-----------|
| Choice 1 | `_global.validate_questionnaire_response?.isFakeWorkflowRequired == true` | `questionnaire_success` (placeholder — update to FakeWorkflow when designed) |
| Default | No rule matches | `questionnaire_success` |

## Output
- **Returns**: No output — routes to the next node.

## Dependencies
- **Reads from _global**: `validate_questionnaire_response.isFakeWorkflowRequired`
- **Expected previous nodes**: `update_questionnaire_incident` (sequentially), `validate_questionnaire_response` (provides data)

## Notes
- **TODO**: When FakeWorkflow is designed, update Choice 1's `nextNode` from `questionnaire_success` to the FakeWorkflow node (SUB_WORKFLOW or CHILD). Both branches currently point to `questionnaire_success` as a placeholder.
- The `isFakeWorkflowRequired` flag comes from the matched question config in `questions_config.json`.
