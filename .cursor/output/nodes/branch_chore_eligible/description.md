# branch_chore_eligible

## Overview
- **ID**: `branch_chore_eligible`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Routes the workflow based on Chore Service eligibility result. Proceeds to fetch the current delivery address if eligible; routes to failure otherwise.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads directly from `_global.chore_eligibility` | Previous node output |

## Script Details

### choices[0].rule (in-node)
- **Logic summary**: Returns `true` if `_global.chore_eligibility.isEligible == true`
- **Script**: `return _global?.chore_eligibility?.isEligible == true`

## Output
- **Routes to**: `get_current_address` (if eligible) or `default_failure` (otherwise)

## Dependencies
- **Reads from `_global`**: `_global.chore_eligibility.isEligible`
- **Expected previous nodes**: `chore_eligibility`
