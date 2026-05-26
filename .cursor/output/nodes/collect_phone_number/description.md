# Collect Phone Number

## Overview
- **ID**: `collect_phone_number`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Placeholder node for the phone number collection step. Will be replaced with actual phone number collection logic (e.g. an INSTRUCTION screen to capture an alternate phone number from the user). Currently just logs and returns an empty map.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | No parameters needed for placeholder | — |

## Script Details

### Transformer (in-node)
- **Location**: Embedded in `transformer.value.data`
- **Logic summary**: Placeholder — prints "phone number collected" and returns an empty map. Replace with actual phone number collection logic when implemented.

## Output
- **Returns**: Empty map `[:]` (placeholder)

## Dependencies
- **Triggered when**: `check_alternate_phone_number_required` evaluates `phoneNumberRequired == true`
- **Next node**: `check_location_screen_required`

## Notes
- This is a placeholder. The intended behaviour is to present a UI screen to collect the customer's alternate phone number, then store it in the workflow context for later use.
- When implementing, likely replace with an INSTRUCTION node or an HTTP node to update the incident with the collected phone number.
