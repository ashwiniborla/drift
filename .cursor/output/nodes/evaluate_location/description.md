# Evaluate Location

## Overview
- **ID**: `evaluate_location`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Calls the AIS location evaluation API to determine whether the delivery partner needs a nudge to locate the customer's address.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | All data sourced directly from workflow context | — |

## Script Details

### URL (in-node)
- **Logic**: Resolves base host from `_enum_store?.clients?.get('ais.ch.host')` and appends `/api/v3/contact/location/evaluate`.
- **Downstream name**: `ais.ch.host` (key in `_enum_store["clients"]`).

### Headers (in-node)
- `Content-Type`: `application/json`
- `X-REQUEST-ID`: from `_global.workflowId`
- `X-PERF-TEST`: from `_global.threadContext.perfFlag`

### Body (in-node)
- `accountId`: `_global.customer.customerId`
- `contactId`: `_global.get_current_address.id`
- `context.marketplace`: hardcoded `"FLIPKART"`
- `context.flowType`: hardcoded `"POST_ORDER"`

### Transformer (in-node)
- Returns full `_response` as-is (contains `location` and `nudge` fields).

## Output
- **Returns**: Full API response stored as `_global.evaluate_location`
  ```json
  {
    "location": { "latitude": "...", "longitude": "...", "driftMeters": 199.0, "updatedAt": 1766149386000 },
    "nudge": { "required": true, "deepLink": "" }
  }
  ```

## Dependencies
- **Reads from _global**: `_global.customer.customerId`, `_global.get_current_address.id`, `_global.workflowId`, `_global.threadContext.perfFlag`
- **Expected previous nodes**: `fetch_location_fetch_oxford` (fetch_order_oxford), and `get_current_address` must be available in parent context

## Notes
- No parameters declared — all data comes from top-level workflow context.
- The `get_current_address.id` field is the contactId (e.g., `CNTCT1A296286EF69407F99D4D0502`).
