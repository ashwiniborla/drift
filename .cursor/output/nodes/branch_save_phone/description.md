# Branch: Should Save Phone?

## Overview
- **ID**: `branch_save_phone`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Routes based on evaluate_alternate_phone_response.savePhone — true → sub_address_update, false → address_phone_change_success.

## Parameters
None.

## Choices

| Condition | nextNode |
|-----------|----------|
| savePhone == true | sub_address_update |
| savePhone == false | address_phone_change_success |
| default | default_failure |

## Dependencies
- **Reads from _global**: `evaluate_alternate_phone_response.savePhone`
- **Expected previous nodes**: `evaluate_alternate_phone_response`
