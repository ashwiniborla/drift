# Check Location Screen Required

## Overview
- **ID**: `check_location_screen_required`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Determines whether the location collection screen should be shown to the user. The decision is based on `postScreensRequired.location` from the validated questionnaire response's selected question config.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `locationRequired` | Boolean flag indicating if location screen is required | `$.validate_questionnaire_response.selectedQuestionConfig.postScreensRequired.location` |

## Script Details

### Rule (in-node)
- **Location**: Embedded in `choices[0].rule.value.data`
- **Context access**: `_global.nodeParameters.locationRequired`
- **Logic summary**: Returns `true` if `locationRequired` is `true`, routing to `collect_location`. Otherwise falls through to `defaultNode`.

## Branch Routing

| Condition | Next Node |
|-----------|-----------|
| `locationRequired == true` | `collect_location` |
| default (false / null) | `questionnaire_end_instruction` |

## Output
- **Returns**: No output. Routes execution to the next node based on the rule.

## Dependencies
- **Reads from _global**: `_global.nodeParameters.locationRequired`
- **Expected previous nodes**: `check_alternate_phone_number_required` (directly, when phone not required) or `collect_phone_number` (after phone collection)

## Notes
- The `locationRequired` parameter value is a boolean from `questions_config.json`'s `postScreensRequired.location` field, surfaced via `validate_questionnaire_response`'s `selectedQuestionConfig`.
- If `locationRequired` is `null` or absent, the default branch (`questionnaire_end_instruction`) is taken.
- In the current `questions_config.json`, all question options have `location: true`, so this branch will almost always route to `collect_location`.
