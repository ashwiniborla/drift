# Resume Request — ask_alternate_phone

## Overview
- **Node ID**: `ask_alternate_phone`
- **Purpose**: Agent/customer provides new phone numbers after reviewing the current delivery address. The workflow is paused (WAITING) until a resume request is received.

---

## Resume scenarios

### Scenario 1: Agent confirms with new primary and alternate phone
- **Description**: Agent enters a new primary phone number and an alternate phone number, then clicks "Update Phone Number".
- **Sample payload**:
```json
{
  "newContact": "9999988888",
  "altContact": "9999977777",
  "action": "CONFIRM"
}
```
- **Outcome**: Workflow proceeds to `create_new_address` to create a new User Service contact with the provided numbers.

---

### Scenario 2: Agent confirms with only a new primary phone (no alternate)
- **Description**: Agent enters only a new primary phone number and leaves the alternate number blank, then clicks "Update Phone Number".
- **Sample payload**:
```json
{
  "newContact": "9999988888",
  "altContact": "",
  "action": "CONFIRM"
}
```
- **Outcome**: Workflow proceeds to `create_new_address`. The `build_create_address_body.groovy` script omits `alt_phone` from the contact creation request when `altContact` is empty.

---

### Scenario 3: Agent cancels the update
- **Description**: Agent clicks "Cancel" without submitting any phone numbers.
- **Sample payload**:
```json
{
  "action": "CANCEL"
}
```
- **Outcome**: Workflow is terminated. The `create_new_address` node will throw an exception due to missing `newContact`, routing to `default_failure`.

---

## Notes
- `newContact` is required — `build_create_address_body.groovy` throws if it is absent
- `altContact` is optional — send empty string or omit the key entirely
- `action` is informational for the UI; the workflow does not branch on it currently
