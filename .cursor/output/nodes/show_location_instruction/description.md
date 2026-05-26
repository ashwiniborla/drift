# Show Location Instruction

## Overview
- **ID**: `show_location_instruction`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Displays the location confirmation screen to the customer. Shows the current delivery address, a deeplink button to open the location sharing page, and success/informational messages. Sets workflow status to COMPLETED — this is a terminal UI interaction; the UI redirects the user to the location page via the deepLink.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `inputOptions` | Full inputOptions array with address and deepLink | `$.prepare_location_instruction.inputOptions` |

## Script Details

### layoutId (STATIC)
- Value: `confirm_delivery_location_screen`

### workflowStatus (SCRIPT)
- Returns: `"COMPLETED"`
- This signals to the client that the workflow is done; no resume is expected after the user taps the deeplink button.

### disposition (SCRIPT)
- Returns: `"COMPLETE"`

### inputOptions
- Uses `possibleDynamicValues: "nodeParameters.inputOptions"` (a **string** hook, not a JSON array — IMS v3 `Option.possibleDynamicValues` is `String`) to inject the full widget list built by `prepare_location_instruction`.

## Output
- This is an INSTRUCTION (WAITING) node — the workflow pauses here while the UI is displayed.
- No meaningful data is written back to `_global` from this node's resume path.

## Dependencies
- **Reads from parameters**: `_global.nodeParameters.inputOptions` (injected from `prepare_location_instruction` output)
- **Expected previous nodes**: `prepare_location_instruction`

## Notes
- `workflowStatus: COMPLETED` means the parent workflow considers this flow done.
- When used as a subworkflow with `includeLastNode: false`, the success terminal node is excluded and the parent's `nextNode` takes over after the instruction completes.
- This node is intentionally not `end: true` in the workflow state — it has `nextNode: fetch_location_success` — but the workflow status COMPLETED is communicated to the UI via the instruction response.
