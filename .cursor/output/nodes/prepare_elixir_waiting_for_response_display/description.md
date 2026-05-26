# prepare_elixir_waiting_for_response_display

## Overview
- **ID**: `prepare_elixir_waiting_for_response_display`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Builds the `inputOptions` list consumed by `elixir_waiting_for_response` via `possibleDynamicValues`, injecting `postFulfillmentData` into template `elixir_ticket_details`.

## Parameters

None.

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/create_elixir_ticket_workflow/prepare_elixir_waiting_for_response_display.groovy`
- **Context access**: `_global.elixir_get_order_details.postFulfillmentData`
- **Logic summary**: Wraps post-fulfillment data in a single Option with tags `imsv2.info` and `templateId` `elixir_ticket_details`.

## Output
- **Returns**: `{ "inputOptions": [ Option ] }` stored under this node’s instance name in workflow context.

## Dependencies
- **Reads from _global**: `elixir_get_order_details.postFulfillmentData`
- **Expected previous node**: `elixir_get_order_details` (or any step that populates that output)

## Notes
- Workflow must run this node **immediately before** `elixir_waiting_for_response`, and the workflow state’s `instanceName` for this node must be exactly `prepare_elixir_waiting_for_response_display` so `possibleDynamicValues` path `prepare_elixir_waiting_for_response_display.inputOptions` resolves.
