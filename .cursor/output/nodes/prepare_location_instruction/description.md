# Prepare Location Instruction

## Overview
- **ID**: `prepare_location_instruction`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Builds the full `inputOptions` array for the `show_location_instruction` INSTRUCTION node. Reads the customer's current delivery address from `_global.get_current_address` and the deepLink URL from the AIS location evaluate response. Optionally includes `request_raised_title` and `request_raised_subtitle` when `showSuccess` resolves to true.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `showSuccess` | Controls whether `request_raised_title` and `request_raised_subtitle` are included. **Boolean** values (typical when bound with JsonPath, e.g. `$.chore_confirm_address.isSuccess`) are interpreted as **chore** success and **inverted** for the UI (`!isSuccess`). **String** values (`"true"` / `"false"`) are the literal UI flag. If the key is **omitted**, the script uses `!_global.chore_confirm_address.isSuccess` when that field exists; otherwise false. | `fetch_location` binds `"showSuccess": "$.chore_confirm_address.isSuccess"` so the apology copy shows when the chore did not succeed. |

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/fetch_location/prepare_location_instruction.groovy`
- **Context access**:
  - `_global.nodeParameters.showSuccess` (optional; see table above)
  - `_global.chore_confirm_address.isSuccess` (used only when `showSuccess` is absent from `nodeParameters`)
  - `_global.get_current_address.address.*` (address fields)
  - `_global.evaluate_location.nudge.deepLink`
- **Logic summary**: Resolves `showSuccess` as above, then constructs `inputOptions`: always `request_raised_header`; conditionally `request_raised_title` and `request_raised_subtitle`; then location section widgets, `static_location`, and `take_location_button` with deepLink.

## Output
- **Returns**: `{ "inputOptions": [ ... ] }` stored as `_global.prepare_location_instruction`

## Dependencies
- **Reads from _global**: `nodeParameters`, optional `chore_confirm_address`, `get_current_address.address`, `evaluate_location.nudge.deepLink`
- **Expected previous nodes**: `evaluate_location`, and `get_current_address` available in parent context when used as a sub-workflow

## Notes
- All address field values are safely navigated with `?.` — missing fields fall back to empty strings.
- The `deepLink` is placed in `submit_location_button.possibleValues[0].metaData.url`.
- **Sync**: After changing `node.json`, sync the node definition to the Drift API/store (e.g. `sync_workflow_nodes.py`) so workers load `parameters: ["showSuccess"]`.
