# branch_minions_eligible

## Overview
- **ID**: `branch_minions_eligible`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Routes the workflow based on whether any units passed Minions eligibility for `CHANGE_SECONDARY_PHONE_NUMBER`. Proceeds to Chore eligibility check if eligible; routes to failure otherwise.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads directly from `_global.check_minions_eligibility` | Previous node output |

## Script Details

### choices[0].rule (in-node)
- **Logic summary**: Returns `true` if `_global.check_minions_eligibility.isEligible == true`
- **Script**: `return _global?.check_minions_eligibility?.isEligible == true`

## Output
- **Routes to**: `chore_eligibility` (if eligible) or `default_failure` (otherwise)

## Dependencies
- **Reads from `_global`**: `_global.check_minions_eligibility.isEligible`
- **Expected previous nodes**: `check_minions_eligibility`
