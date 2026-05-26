# Ask: Collect Alternate Phone Number (Smart Action)

## Overview
- **ID**: `ask_alternate_phone_smart`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Smart-action version of the phone collection instruction. Displays current address info and collects `newContact` and `altContact` from the user/operator. Has the same resume payload shape as `ask_alternate_phone` so downstream nodes (`create_new_address`, `chore_confirm_address`) can reference the collected data via the same context key.

## contextOverrideKey
In the smart parent workflow state, this node is assigned `contextOverrideKey: "ask_alternate_phone"`. This means:
- Context is stored at `_global['ask_alternate_phone']` (same as the non-smart `ask_alternate_phone` node)
- Resume data is accessible as `_global['ask_alternate_phone:viewResponse'].selectedOptions.newContact`
- `create_new_address` and `chore_confirm_address` scripts work unchanged for both paths

## Script Details
- `layoutId`: static `"alternate_phone_input_smart"` (placeholder; final layout TBD)

## Output
Stored at `_global['ask_alternate_phone:viewResponse'].selectedOptions`:
```json
{
  "newContact": "9123456789",
  "altContact": "9123456780"
}
```

## Dependencies
- **Must run after**: `fap_eligibility_check` sub-workflow (provides address context in `_global.get_current_address`)
- **Used in workflow**: `forward_address_phone_change_smart`

## Notes
- Contracts (inputOptions/layout) are TBD. Will differ from `ask_alternate_phone` in UI presentation but must produce identical resume payload.
