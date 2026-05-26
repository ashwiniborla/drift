# Prepare Questionnaire End Instructions

## Overview
- **ID**: `prepare_questionnaire_end_instructions`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Builds the `inputOptions` list for the questionnaire end screen (`request_raised_to_delivery_team_end`). Returns a static success message and a Submit button with `redirect: true` in metaData. Keeping this in a script allows iris keys and text to be changed without modifying the INSTRUCTION node definition.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | End screen content is static — no runtime parameters needed | — |

## Script Details

### Transformer (dynamic)
- **Location**: `src/main/resources/scripts/fake_questionnaire/prepare_questionnaire_end_instructions.groovy`
- **Context access**: No `_global` fields required — content is static.
- **Logic summary**: Builds and returns a list with two option maps:
  1. `request_raised_to_delivery_team_message` — `ss.static_text` using `iris_static_message` template (enum: `request_raised_to_delivery_team`)
  2. `submit_button` — `ss.button_widget` with a single `SUBMIT` possible value and `metaData: { redirect: true }` to signal the UI to redirect instead of calling the resume API.

## Output
- **Returns**: `Map { inputOptions: List<OptionMap> }` — stored in `_global.prepare_questionnaire_end_instructions`
- **Consumed by**: `questionnaire_end_instruction` INSTRUCTION node via `possibleDynamicValues: "prepare_questionnaire_end_instructions.inputOptions"`

## Dependencies
- **Reads from _global**: Nothing
- **Expected previous nodes**: `collect_location` (when location required) or `check_location_screen_required` (when location not required)

## Notes
- The `redirect: true` metaData on the Submit button tells the client not to call the resume workflow API — the workflow terminates here (`end: true` on the workflow state).
- To change iris keys or default text for the success message, update only this groovy script — no node definition change required.
