# prepare_alternate_phone_response

## Overview
- **ID**: `prepare_alternate_phone_response`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Reads `get_current_address` from workflow context and dynamically builds the `inputOptions` list for the `ask_alternate_phone` INSTRUCTION node. Handles two scenarios — when an alternate phone is absent and when one is already present — using conditional logic only on the fields that differ between the two UI contracts.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| _(none)_ | Reads `_global.get_current_address` directly | Previous node output |

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/prepare_alternate_phone_response.groovy`
- **Context access**: `_global.get_current_address`
- **Logic summary**:
  1. Extracts `customerName` from `get_current_address.name.input`
  2. Finds `primaryPhone` (first `communications` entry with `type == "mobile"`)
  3. Finds `altPhone` (first `communications` entry with `type == "alt_phone"`) — `null` if absent
  4. Formats phone numbers with `+91` prefix for display; strips country code for input field `defaultValue`
  5. Builds common `inputOptions` unconditionally (header, success popup title/subtitle, main title, confirm button, all modal fields)
  6. Uses `if/else` **only** on:
     - Subtitle (`irisKey`/`defaultText`)
     - `primary_contact_card` (tags and `templateVariables`)
     - `add_alternate_number_row` (only when no alt phone)
     - `close_button_widget` (only when alt phone present)
     - `reciever_alternate_phone_number.defaultValue` (empty vs actual alt phone digits)

## Scenarios

| Scenario | `hasAltPhone` | Subtitle irisKey | Contact card tags | `add_alternate_number_row` | `close_button_widget` |
|----------|---------------|------------------|-------------------|----------------------------|-----------------------|
| No alt phone | `false` | `confirm_contact_and_add_alternate_subtitle` | `ss.contact_card` | present | absent |
| Alt phone present | `true` | `confirm_contact_details_subtitle` | `ss.contact_card_primary_secondary` | absent | present |

## Output
- **Returns**: `[inputOptions: <List>]`
- **Stored in `_global`**: `_global.prepare_alternate_phone_response.inputOptions`
- Consumed by the next node `ask_alternate_phone` via workflow parameter binding `$.prepare_alternate_phone_response.inputOptions`

## Dependencies
- **Reads from `_global`**: `_global.get_current_address`
- **Expected previous nodes**: `branch_feasibility` (routes here on `isFeasible == true`)

## Notes
- Modal fields (`change_or_add_number_title`, `reciever_name`, `reciever_phone_number`, `reciever_alternate_phone_number`, `delivery_communications_info`, `update_button_widget`) always use `parentId: "add_alternate_number_row"` regardless of scenario
- `altPhone` is identified by `communications[].type == "alt_phone"` (not `mobile`)
