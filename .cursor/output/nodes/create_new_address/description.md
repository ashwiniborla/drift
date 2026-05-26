# create_new_address

## Overview
- **ID**: `create_new_address`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Creates a new contact in User Service by copying all existing physical address fields from the current address and substituting the new primary and alternate phone numbers provided by the agent.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads address fields and new phone numbers directly from `_global` | Previous node outputs |

## Script Details

### url (in-node)
- **Downstream name**: `user_svc.host` (key in `_enum_store.clients`)
- **Script**: Builds `<user_svc.host>/contacts/<customerId>` using `_global.customer.customerId`

### headers (in-node)
- **Script**: Returns `Content-Type: application/json` and `X-PERF-TEST`.

### body (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/build_create_address_body.groovy`
- **Logic summary**:
  1. Copies all physical address fields from `_global.get_current_address` (name, addressLine1/2, city, state, country, pincode, landmark, locationTypeTag)
  2. Builds `communications` array with `mobile` = `_global.ask_alternate_phone.newContact`
  3. Appends `alt_phone` entry only if `altContact` is non-empty
  4. Throws if `newContact` is missing

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/transform_create_address.groovy`
- **Logic summary**: Extracts `id` from the response as `newAddressId`; throws if absent

## Output
- **Returns** (stored in `_global.create_new_address`):
```json
{
  "newAddressId": "CNTCT_NEW_789ABC"
}
```

## Dependencies
- **Reads from `_global`**: `_global.customer.customerId`, `_global.get_current_address.*`, `_global.ask_alternate_phone.newContact`, `_global.ask_alternate_phone.altContact`
- **Expected previous nodes**: `get_current_address`, `ask_alternate_phone`

## Notes
- Physical address is never modified — only phone numbers change
- Client key `user_svc.host` must be registered in `_enum_store.clients`
