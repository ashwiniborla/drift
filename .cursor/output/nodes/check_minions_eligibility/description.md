# check_minions_eligibility

## Overview
- **ID**: `check_minions_eligibility`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Reads units from the Oxford order response and filters those eligible for `CHANGE_SECONDARY_PHONE_NUMBER` based on the Minions `unitChangeActions`. Also extracts the `deliveryAddressId` from the first eligible unit.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads directly from `_global.fetch_order_oxford` | Previous node output |

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/check_minions_eligibility.groovy`
- **Context access**: `_global.fetch_order_oxford.units`, `_global.fetch_order_oxford.targetUnitIds`
- **Logic summary**:
  1. Iterates each unit ID from `targetUnitIds`
  2. Finds the corresponding unit in the `units` map
  3. Checks `unitChangeActions` for an entry with `actionType == CHANGE_SECONDARY_PHONE_NUMBER` and `eligibility == true`
  4. Collects eligible unit IDs and captures `toParty.deliveryAddressId` from the first match

## Output
- **Returns** (stored in `_global.check_minions_eligibility`):
```json
{
  "eligibleUnitIds": ["436921306442895100000"],
  "deliveryAddressId": "CNTCT_EXISTING_001",
  "isEligible": true
}
```

## Dependencies
- **Reads from `_global`**: `_global.fetch_order_oxford.units`, `_global.fetch_order_oxford.targetUnitIds`
- **Expected previous nodes**: `fap_eligibility_extract_oxford_units` (GROOVY, resourceId: `extract_oxford_order_units`), which overwrites `fetch_order_oxford` with `{units, targetUnitIds}`. The full pipeline is: `fap_eligibility_fetch_oxford` (HTTP, raw Oxford) → `fap_eligibility_extract_oxford_units` (GROOVY, extract units) → `check_minions_eligibility`.

## Notes
- If no units match, `isEligible` is `false` and `deliveryAddressId` is `null`
- Downstream branch node `branch_minions_eligible` reads `_global.check_minions_eligibility.isEligible`
