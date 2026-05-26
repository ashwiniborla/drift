# Fake Workflow Show Instructions

## Overview
- **ID**: `fake_workflow_show_instructions`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Displays fake delivery attempt instructions to the SA agent, including product details, shipping/delivery dates, and step-by-step instructions for customer communication.

## Parameters

None (static contract for now; template variables will be made dynamic later).

## Layout
- **Layout ID**: `elixir_fake_attempt` (STATIC)

## Input Options

| Option ID | Type | Description |
|-----------|------|-------------|
| `fake_delivery_attempt` | `sa.static_text` | Multi-template block: customer message, order items with product images, and detailed instructions |
| `fake_delivery_attempt_proceed_button_widget` | `sa.button_widget` | "Done" button to proceed |

## Output
- **Resume**: Agent clicks "Done" button, resume payload contains `fake_delivery_attempt_proceed_button_widget: "DONE"`

## Dependencies
- **Expected previous nodes**: `fake_client_check` (SA path)

## Notes
- Template variables (productDetails, dates, status) are currently static/illustrative. Will be replaced with dynamic values from context in a future iteration.
- Only shown to SA clients (guarded by `fake_client_check` branch).
