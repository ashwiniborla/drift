# chore_eligibility

## Overview
- **ID**: `chore_eligibility`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Calls the Chore Service eligibility API to verify whether the order units are eligible for a post-dispatch address change. Checks `unitEligibilityList` for the `CHANGE_ADDRESS_TEXT` option.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads `orderId` and `eligibleUnitIds` directly from `_global` | Previous node outputs |

## Script Details

### url (in-node)
- **Downstream name**: `chore.host` (key in `_enum_store.clients`)
- **Script**: `return (_enum_store?.clients?.get('chore.host') ?: '').toString() + '/api/v3/eligibility/addressChangeEligibility'`

### headers (in-node)
- **Script**: Returns `Content-Type: application/json`, `x-client-id: self_serve`, and `X-PERF-TEST` from thread context.

### body (in-node)
- **Script**: Builds `{ orderVersion: -1, orderId, orderItemUnitIds }` from `_global.orderDetails[0].orderId` and `_global.check_minions_eligibility.eligibleUnitIds`

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/transform_chore_eligibility.groovy`
- **Logic summary**:
  1. Reads `unitEligibilityList` from response
  2. Checks each entry's `eligibleOptions` for `CHANGE_ADDRESS_TEXT`
  3. Returns `choreId`, `actionableUnitIds`, and `isEligible`

## Output
- **Returns** (stored in `_global.chore_eligibility`):
```json
{
  "choreId": "CU103696483280830262",
  "actionableUnitIds": ["436921306442895100000"],
  "isEligible": true
}
```

## Dependencies
- **Reads from `_global`**: `_global.orderDetails[0].orderId`, `_global.check_minions_eligibility.eligibleUnitIds`
- **Expected previous nodes**: `check_minions_eligibility`

## Notes
- `choreId` from this response is carried through to `chore_feasibility` and `chore_confirm_address`
- Client key `chore.host` must be registered in `_enum_store.clients`
