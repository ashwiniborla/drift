# Resume Request — Ask: Collect Alternate Phone Number (Smart Action)

## Overview
- **Node ID**: `ask_alternate_phone_smart`
- **Purpose**: Collects the new primary phone number and alternate phone number from the operator/customer in the smart-action context. Resume payload is identical in shape to `ask_alternate_phone`.

## Resume scenarios

### Scenario 1: User confirms with both phone numbers
- **Description**: Operator enters a new primary contact and an alternate contact, then confirms.
- **Sample request / payload**:
  ```json
  {
    "selectedOptions": {
      "newContact": "9123456789",
      "altContact": "9876543210"
    }
  }
  ```
- **Outcome**: Workflow continues to `sub_address_update` → `create_new_address` → `chore_confirm_address` → `fap_success_instruction`.

### Scenario 2: User confirms with only new primary phone (no alternate)
- **Description**: Operator enters only the new primary contact.
- **Sample request / payload**:
  ```json
  {
    "selectedOptions": {
      "newContact": "9123456789",
      "altContact": ""
    }
  }
  ```
- **Outcome**: Workflow continues to `sub_address_update`. `create_new_address` uses an empty alt phone.

### Scenario 3: User cancels
- **Description**: Operator selects Cancel, aborting the phone change.
- **Sample request / payload**:
  ```json
  {
    "selectedOptions": {
      "confirm_phone_button": "CANCEL"
    }
  }
  ```
- **Outcome**: Workflow behaviour on cancel TBD (depends on post-cancellation routing, to be specified with contracts).

## Notes
- The context key for resume data is always `ask_alternate_phone` (due to `contextOverrideKey`), not `ask_alternate_phone_smart`.
- Downstream scripts access: `_global['ask_alternate_phone:viewResponse'].selectedOptions.newContact` and `.altContact`.
