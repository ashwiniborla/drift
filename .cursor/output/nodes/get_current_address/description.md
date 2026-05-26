# get_current_address

## Overview
- **ID**: `get_current_address`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Fetches the current delivery address details from User Service using `customerId` and `deliveryAddressId`. The address details (pincode, phone, name, etc.) are used in subsequent feasibility and contact-creation steps.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads `customerId` and `deliveryAddressId` directly from `_global` | Previous node outputs |

## Script Details

### url (in-node)
- **Downstream name**: `user_svc.host` (key in `_enum_store.clients`)
- **Script**: Builds `<user_svc.host>/contacts/<customerId>/<deliveryAddressId>` using `_global.customer.customerId` and `_global.check_minions_eligibility.deliveryAddressId`

### headers (in-node)
- **Script**: Returns `Content-Type: application/json` and `X-PERF-TEST` from thread context.

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/transform_current_address.groovy`
- **Logic summary**:
  1. Extracts `name`, `address` fields (addressLine1/2, city, state, country, pincode, landmark), and `locationTypeTag`
  2. Extracts `mobile` and `alt_phone` from `communications` array
  3. Validates that `pincode` is present (throws if missing)

## Output
- **Returns** (stored in `_global.get_current_address`):
```json
{
  "id": "CNTCT_EXISTING_001",
  "name": "John Doe",
  "phone": "9876543210",
  "alt_phone": "9876500000",
  "pincode": "226017",
  "addressLine1": "123 Main Street",
  "addressLine2": "Apartment 4B",
  "city": "Lucknow",
  "state": "Uttar Pradesh",
  "country": "India",
  "landmark": "Near City Mall",
  "address_location_tag": "HOME"
}
```

## Dependencies
- **Reads from `_global`**: `_global.customer.customerId`, `_global.check_minions_eligibility.deliveryAddressId`
- **Expected previous nodes**: `check_minions_eligibility`

## Notes
- `pincode` is required — the transformer throws if it is absent
- The `pincode` value is reused as both `oldPincode` and `newPincode` in feasibility and confirm requests (phone-only change)
- Client key `user_svc.host` must be registered in `_enum_store.clients`
