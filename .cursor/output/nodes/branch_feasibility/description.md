# branch_feasibility

## Overview
- **ID**: `branch_feasibility`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Routes the workflow based on the Chore feasibility check. Proceeds to ask the agent for new phone numbers if feasible; routes to failure otherwise. This gate runs *before* asking for user input to avoid unnecessary interaction when the change cannot be completed.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads directly from `_global.chore_feasibility` | Previous node output |

## Script Details

### choices[0].rule (in-node)
- **Logic summary**: Returns `true` if `_global.chore_feasibility.isFeasible == true`
- **Script**: `return _global?.chore_feasibility?.isFeasible == true`

## Output
- **Routes to**: `ask_alternate_phone` (if feasible) or `default_failure` (otherwise)

## Dependencies
- **Reads from `_global`**: `_global.chore_feasibility.isFeasible`
- **Expected previous nodes**: `chore_feasibility`
