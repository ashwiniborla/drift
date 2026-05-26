# chore_feasibility

## Overview
- **ID**: `chore_feasibility`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Calls the Chore Service feasibility API to verify that the address change can proceed. Since only the phone number is changing (not the physical address), `newPincode` and `oldPincode` are identical.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `fieldsUpdatedV2` | Required; included in POST body | e.g. `fap_eligibility_check` sets `SECONDARY_PHONE_NUMBER` on this node |
| — | `choreId`, unit ids, `pincode` from `_global` | `chore_eligibility`, `get_current_address` |

## Script Details

### url (in-node)
- **Downstream name**: `chore.host` (key in `_enum_store.clients`)
- **Script**: `return (_enum_store?.clients?.get('chore.host') ?: '').toString() + '/api/v3/order/changeAddressFeasibility/v2'`

### headers (in-node)
- **Script**: Returns `Content-Type: application/json`, `x-client-id: self_serve`, and `X-PERF-TEST`.

### body (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/build_chore_feasibility_body.groovy`
- **Logic summary**: Builds `{ choreId, orderVersion: -1, orderId, orderItemUnitIds, newPincode, oldPincode, fieldsUpdatedV2 }` — pincode is same for both fields

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/transform_chore_feasibility.groovy`
- **Logic summary**: Returns `isFeasible = nonActionableUnits.isEmpty()` — any unit in `non_actionable_units` marks the check as failed

## Output
- **Returns** (stored in `_global.chore_feasibility`):
```json
{
  "actionableUnitIds": ["436921306442895100000"],
  "nonActionableUnitIds": [],
  "isFeasible": true
}
```

## Dependencies
- **Reads from `_global`**: `_global.chore_eligibility.choreId`, `_global.chore_eligibility.actionableUnitIds`, `_global.orderDetails[0].orderId`, `_global.get_current_address.pincode`
- **Expected previous nodes**: `chore_eligibility`, `get_current_address`

## Notes
- `isFeasible` is based on `nonActionableUnits` being empty, not on `actionableUnits` being non-empty
- `choreId` from the eligibility step is passed through — Chore requires it for state continuity
