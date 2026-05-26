# Branch: Nudge Required

## Overview
- **ID**: `branch_nudge_required`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Routes the workflow based on whether the AIS location evaluation API determined that a nudge (deeplink to location sharing screen) is required for the customer.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Reads directly from workflow context | — |

## Script Details

### Choice 1 — nudge required
- **Rule**: `_global?.evaluate_location?.nudge?.required == true`
- **Next node**: `prepare_location_instruction`

### Choice 2 — nudge not required
- **Rule**: `_global?.evaluate_location?.nudge?.required == false`
- **Next node**: `fetch_location_success`

### Default node
- `default_failure` — fires when `nudge.required` is null or the field is missing.

## Output
- No direct output (BRANCH nodes produce routing only).

## Dependencies
- **Reads from _global**: `_global.evaluate_location.nudge.required`
- **Expected previous nodes**: `evaluate_location`

## Notes
- Rules are evaluated in order; the first match wins.
- If the API response is missing the `nudge` object entirely, `defaultNode` (`default_failure`) is triggered.
