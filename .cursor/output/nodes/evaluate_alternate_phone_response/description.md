# Evaluate Alternate Phone Response

## Overview
- **ID**: `evaluate_alternate_phone_response`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Evaluates ask_alternate_phone viewResponse to determine if the alternate phone should be saved (sub_address_update) or workflow should complete without saving (address_phone_change_success).

## Parameters
None.

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/forward_address_phone_change/evaluate_alternate_phone_response.groovy`
- **Context access**: `_global['ask_alternate_phone:viewResponse']?.selectedOptions`
- **Logic summary**:
  - If `confirm_details_button == "CONFIRM_DETAILS"` → savePhone = false
  - Else if `reciever_alternate_phone_number != null` AND `update_button_widget == "UPDATE"` → savePhone = true
  - Else → savePhone = false

## Output
- **Returns**: `[savePhone: boolean]` — stored in `_global.evaluate_alternate_phone_response`

## Dependencies
- **Reads from _global**: `ask_alternate_phone:viewResponse.selectedOptions`
- **Expected previous nodes**: `ask_alternate_phone`

## Notes
- `reciever_alternate_phone_number` uses the existing typo from prepare_alternate_phone_response.
- `update_button_widget` value is compared case-insensitively (config uses `'update'`).
