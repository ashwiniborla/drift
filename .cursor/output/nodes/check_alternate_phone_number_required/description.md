# Check Alternate Phone Number Required

## Overview
- **ID**: `check_alternate_phone_number_required`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Determines whether the phone number collection screen should be shown to the user. The decision is based on `postScreensRequired.phone` from the validated questionnaire response's selected question config.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `phoneNumberRequired` | Boolean flag indicating if phone number screen is required | `$.validate_questionnaire_response.selectedQuestionConfig.postScreensRequired.phone` |

## Script Details

### Rule (in-node)
- **Location**: Embedded in `choices[0].rule.value.data`
- **Context access**: `_global.nodeParameters.phoneNumberRequired`
- **Logic summary**: Returns `true` if `phoneNumberRequired` is `true`, routing to `collect_phone_number`. Otherwise falls through to `defaultNode`.

## Branch Routing

| Condition | Next Node |
|-----------|-----------|
| `phoneNumberRequired == true` | `collect_phone_number` |
| default (false / null) | `check_location_screen_required` |

## Output
- **Returns**: No output. Routes execution to the next node based on the rule.

## Dependencies
- **Reads from _global**: `_global.nodeParameters.phoneNumberRequired`
- **Expected previous nodes**: `check_fake_workflow_required` (directly, when fake workflow not required) or `run_fake_sub_workflow` (after inline sub-workflow completes)

## Notes
- The `phoneNumberRequired` parameter value is a boolean from `questions_config.json`'s `postScreensRequired.phone` field, surfaced via `validate_questionnaire_response`'s `selectedQuestionConfig`.
- If `phoneNumberRequired` is `null` or absent, the default branch (`check_location_screen_required`) is taken.
