# chore_confirm_address

## Overview
- **ID**: `chore_confirm_address`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Calls the Chore Service confirm API to commit the address change. Uses the newly created contact ID from User Service. Since only the phone number changed, `newPincode` equals `oldPincode` and `fieldsUpdated` is `TEXT_AND_PHONE_NUMBER`.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads all required fields directly from `_global` | Previous node outputs |

## Script Details

### url (in-node)
- **Downstream name**: `chore.host` (key in `_enum_store.clients`)
- **Script**: `return (_enum_store?.clients?.get('chore.host') ?: '').toString() + '/api/v3/order/changeAddressConfirm/v2'`

### headers (in-node)
- **Script**: Returns `Content-Type: application/json`, `x-client-id: self_serve`, and `X-PERF-TEST`.

### body (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/build_chore_confirm_body.groovy`
- **Logic summary**: Builds confirm payload with `choreId` from eligibility step, `addressId` = `newAddressId` from User Service, same pincode for old/new, `fieldsUpdated: TEXT_AND_PHONE_NUMBER`, `shouldUnhold: false`

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/transform_chore_confirm.groovy`
- **Logic summary**: Checks `status == SUCCESS`; throws if status is not SUCCESS

## Output
- **Returns** (stored in `_global.chore_confirm_address`):
```json
{
  "choreId": "CU103696483280830262",
  "status": "SUCCESS",
  "isSuccess": true
}
```

## Dependencies
- **Reads from `_global`**: `_global.chore_eligibility.choreId`, `_global.chore_feasibility.actionableUnitIds`, `_global.orderDetails[0].orderId`, `_global.get_current_address.pincode`, `_global.create_new_address.newAddressId`
- **Expected previous nodes**: `create_new_address`, `chore_feasibility`, `chore_eligibility`

## Notes
- `fieldsUpdated: TEXT_AND_PHONE_NUMBER` covers both address text and phone — correct value even for phone-only change
- `shouldUnhold: false` — no hold management needed in this flow
- If status is not SUCCESS, the transformer throws, routing to `default_failure`
