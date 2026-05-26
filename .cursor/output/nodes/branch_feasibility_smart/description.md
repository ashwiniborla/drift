# Branch: Is Address Change Feasible? (Smart Action)

## Overview
- **ID**: `branch_feasibility_smart`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Evaluates the Chore feasibility result after the `fap_eligibility_check` sub-workflow is inlined into the smart parent. Routes feasible cases to `ask_alternate_phone_smart`; infeasible cases to `default_failure` (which in the smart parent spawns the non-smart child workflow).

## Branch Logic

| Condition | Next node |
|-----------|-----------|
| `_global?.chore_feasibility?.isFeasible == true` | `ask_alternate_phone_smart` |
| default (false or null) | `default_failure` |

## Dependencies
- **Reads from _global**: `chore_feasibility.isFeasible` (set by `chore_feasibility` HTTP node inlined from `fap_eligibility_check`)
- **Used in workflow**: `forward_address_phone_change_smart`

## Notes
- Counterpart to `branch_feasibility` used in the non-smart workflow. Same rule, different `nextNode` (routes to smart instruction instead of non-smart instruction).
