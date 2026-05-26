# Add Notes Instruction

## Overview
- **ID**: `add_notes_instruction`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Presents a notes screen to the SA agent with a free-text field and an "Update Incident" button to capture agent notes.

## Parameters

None.

## Layout
- **Layout ID**: `add_notes_instruction` (STATIC)

## Input Options

| Option ID | Type | Description |
|-----------|------|-------------|
| `add_notes_static_text` | `sa.static_text` | Static text header using `add_notes_template` |
| `agent_notes` | `sa.free_text` | Free-text input for agent notes using `take_notes_template` |
| `add_notes_button_widget` | `sa.button_widget` | "Update Incident" button to submit notes |

## Output
- **Resume**: Agent enters notes and clicks "Update Incident", resume payload contains `agent_notes: "<text>"` and `add_notes_button_widget: "UPDATE_INCIDENT"`

## Dependencies
- **Expected previous nodes**: `fake_workflow_show_instructions`

## Notes
- This is the last interactive screen before workflow success.
