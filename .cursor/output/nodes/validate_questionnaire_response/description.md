# validate_questionnaire_response

## Overview
- **ID**: `validate_questionnaire_response`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Validates the user's selected questionnaire option against the known question keys from the config. Stores the matched option config, determines `questionnaireType` (FAKE_FORWARD / FAKE_REVERSE), and extracts `isFakeWorkflowRequired` for downstream branching.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `questions` | Questions config block (title, subTitle, questions list) | `$.process_fake_workflow_details.questions` |
| `flowDirection` | FORWARD or REVERSE | `$.process_fake_workflow_details.flowDirection` |

## Script Details

### transformer (dynamic)
- **Location**: `worker/src/main/resources/scripts/fake_questionnaire/validate_questionnaire_response.groovy`
- **Context access**: `_global['questionnaire_instructions:viewResponse']?.selectedOptions`, `_global.nodeParameters.questions`, `_global.nodeParameters.flowDirection`
- **Logic summary**: Reads the user's selected option from the instruction resume response (`elixir_questionnaire_preference` widget). Matches against the questions config list by `key`. Extracts `isFakeWorkflowRequired` and `nextWorkflow`. Determines `questionnaireType` from `flowDirection`. Builds `questionnaireData` object. Also detects which button was pressed (raise/close).

## Output
- **Returns**: Map stored in `_global.validate_questionnaire_response`:
  - `isValid` (boolean)
  - `selectedOptionKey` (String)
  - `selectedQuestionConfig` (Map — the matched question config entry)
  - `isFakeWorkflowRequired` (boolean)
  - `nextWorkflow` (String or null)
  - `questionnaireType` (String — FAKE_FORWARD or FAKE_REVERSE)
  - `questionnaireData` (Map — { questionnaireType, response, createdAt })
  - `buttonAction` (String — RAISE, CLOSE, or UNKNOWN)

## Dependencies
- **Reads from _global**: `questionnaire_instructions:viewResponse.selectedOptions`, `nodeParameters.questions`, `nodeParameters.flowDirection`
- **Expected previous nodes**: `process_fake_workflow_details` (provides questions, flowDirection), `questionnaire_instructions` (provides viewResponse)

## Notes
- If `selectedOptionKey` does not match any question key, `isValid` = false and `isFakeWorkflowRequired` = false.
- `buttonAction` distinguishes between the "Raise to delivery team" button (`RAISE`) and the "Close" button (`CLOSE`).
