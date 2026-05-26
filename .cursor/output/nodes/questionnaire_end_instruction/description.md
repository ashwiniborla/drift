# Questionnaire End Instruction

## Overview
- **ID**: `questionnaire_end_instruction`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Terminal end screen shown to the user after the questionnaire post-screens (phone / location) are complete. Displays a "request raised to delivery team" confirmation message and a Submit button with `redirect: true` in metaData, which tells the UI to redirect rather than call the resume workflow API.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `inputOptions` | Pre-built list of option maps (static text + Submit button) | `$.prepare_questionnaire_end_instructions.inputOptions` |

## Widget Structure

The `inputOptions` are built dynamically by the `prepare_questionnaire_end_instructions` GROOVY node and passed via `possibleDynamicValues`. This keeps iris keys and default text decoupled from the node definition.

| Option ID | Tag | Content |
|-----------|-----|---------|
| `request_raised_to_delivery_team_message` | `ss.static_text` | `iris_static_message` template with `enum: request_raised_to_delivery_team` |
| `submit_button` | `ss.button_widget` | Single `SUBMIT` value with `metaData: { redirect: true }` |

## Layout

- **layoutId**: `request_raised_to_delivery_team_end` (static)

## Output
- **Workflow state**: `end: true` — the workflow terminates here; no resume is expected.
- Because `redirect: true` is set on the Submit button, the UI redirects without calling the resume API.

## Dependencies
- **Reads from _global**: `_global.nodeParameters.inputOptions` (passed from `prepare_questionnaire_end_instructions.inputOptions`)
- **Expected previous nodes**: `prepare_questionnaire_end_instructions` must run immediately before this node

## Notes
- `disposition` and `workflowStatus` are intentionally omitted — not required for this screen.
- To update the message text or iris enum key, change only `prepare_questionnaire_end_instructions.groovy` — no node definition update needed.
