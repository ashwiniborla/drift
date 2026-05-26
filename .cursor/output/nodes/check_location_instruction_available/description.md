# check_location_instruction_available

## Overview
- **ID**: `check_location_instruction_available`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Route to location instruction when location data exists; otherwise route to questionnaire end instructions.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Uses workflow context directly from `_global` | `_global.prepare_location_instruction` |

## Script Details

### choices[0].rule (in-node)
- **Location**: embedded
- **Context access**: `_global.prepare_location_instruction`
- **Logic summary**: Returns `true` when `prepare_location_instruction` is null or empty (collection/map/string empty).

### choices[1].rule (in-node)
- **Location**: embedded
- **Context access**: none
- **Logic summary**: Always `true`; routes remaining cases to `show_location_instruction`.

## Output
- **Returns**: Boolean route decision from branch rules (internal branch evaluation output).

## Dependencies
- **Reads from _global**: `prepare_location_instruction`
- **Expected previous nodes**: `collect_location`

## Notes
- `defaultNode` is `prepare_questionnaire_end_instructions`.
- Routing behavior:
  - null/empty -> `prepare_questionnaire_end_instructions`
  - non-empty -> `show_location_instruction`
