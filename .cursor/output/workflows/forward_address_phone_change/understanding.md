# Forward Address Phone Change — Workflow Understanding Document

## Purpose

This document captures the complete understanding of the **Alternate Phone Number Update** workflow for Drift (IMSv3). It is the primary reference for generating `workflow.json`, node definitions, and Groovy scripts.

---

## What This Workflow Does

Updates the alternate (and primary) phone number on a customer's delivery address for a forward (delivery) order — without changing the physical address or pincode.

The action-server equivalent is the `ADD_ALTERNATE_CONTACT` sub-flow of `ForwardAddressChangeAction`.

---

## Source of Truth: Action-Server Code

- **Action class:** `ForwardAddressChangeAction.java`
- **Task class:** `ForwardAddressChangeTask.java`
- **Sub-flow:** `ForwardAddressChangeActionType.ADD_ALTERNATE_CONTACT`

Key methods ported:
- `getAddressChangeEligibilityBatch` → Oxford fetch + Minions check + Chore eligibility
- `isNewAddressFeasible` (ADD_ALTERNATE_CONTACT branch) → Chore feasibility with oldPincode == newPincode
- `getSavedCustomerAddresses` / `getCurrentShippingAddress` → User Service getContact
- `changeAddress` (ADD_ALTERNATE_CONTACT branch) → `getNewAddressWithAltNum` + `createContactInfo` + Chore confirm
- `getChangedField` → always `PHONE_NUMBER` for phone-only change (pincode and text unchanged)

---

## Key Design Decision: Reordered Flow

In the action-server, feasibility is checked **after** the user enters a phone number. This is wasteful — if the address isn't feasible (e.g. item already out for delivery), the user entered a number for nothing.

In the Drift workflow, feasibility is checked **before** the INSTRUCTION node:

```
... eligibility → get address (for pincode) → feasibility → INSTRUCTION (ask phone) → create address → confirm
```

This is valid because for `ADD_ALTERNATE_CONTACT`, `newPincode == oldPincode` (address doesn't change), so feasibility can be evaluated upfront with the current address pincode.

---

## Workflow Start Params (`_global`)

```json
{
  "customer": { "customerId": "ACC0327EB6F896B4ABA8699150CFA27F629A" },
  "orderDetails": [
    {
      "orderId": "OD436921306442895100",
      "orderItemId": "436921306442895100",
      "orderItemUnitId": "436921306442895100000",
      "trackingId": "FMPC5831452742"
    }
  ],
  "params": {
    "workflowId": "forward_address_phone_change",
    "version": "SNAPSHOT",
    "issueId": "XXXX",
    "incidentId": "INXXXXXXX"
  },
  "incidentId": "INXXXXXXX",
  "issueDetail": {}
}
```

Key paths:
- `orderId`: `_global.orderDetails[0].orderId`
- `unitIds`: `_global.orderDetails.collect { it.orderItemUnitId }`
- `customerId`: `_global.customer.customerId`

---

## Complete Workflow Flow

```
fetch_order_oxford (HTTP)
    |
check_minions_eligibility (GROOVY)
    |
branch_minions_eligible (BRANCH)
    |-- NOT ELIGIBLE --> not_eligible_failure (FAILURE)
    |-- ELIGIBLE ----->
         |
    chore_eligibility (HTTP)
         |
    branch_chore_eligible (BRANCH)
         |-- NOT ELIGIBLE --> not_eligible_failure (FAILURE)
         |-- ELIGIBLE ----->
              |
         get_current_address (HTTP)
              |
         chore_feasibility (HTTP)
              |
         branch_feasibility (BRANCH)
              |-- NOT FEASIBLE --> not_feasible_failure (FAILURE)
              |-- FEASIBLE ----->
                   |
              ask_alternate_phone (INSTRUCTION) <-- user enters newContact + altContact
                   |
              create_new_address (HTTP)
                   |
              chore_confirm_address (HTTP)
                   |
              fap_workflow_success (SUCCESS)
```

---

## Node Details

### 1. `fetch_order_oxford` (HTTP)

**Reuses:** `e2e_fetch_order_details` node definition (same Oxford API call).

**State parameters:**
- `dataVariable`: `v2OrderData_cs_controller_client_check_default`
- `useCase`: `default`

**What it fetches:**
From the Oxford resolved-variables API, parses the raw OMS3 order data. The transformer (`extract_order_units.groovy`) returns:
```json
{
  "units": {
    "<unitId>": {
      "status": "READY_TO_SHIP",
      "subStatus": "OUT_FOR_DELIVERY",
      "toParty": { "deliveryAddressId": "CNTCT...", "accountId": "ACC..." },
      "unitChangeActions": [
        { "actionType": "POST_DISPATCH_CHANGE_ADDRESS", "eligibility": true, "reason": "VALID_PARAM_PASSED" }
      ],
      "chores": []
    }
  },
  "targetUnitIds": ["<unitId>"]
}
```

**Output stored at:** `_global.fetch_order_oxford`

---

### 2. `check_minions_eligibility` (GROOVY)

**Script:** `forward_address_phone_change/check_minions_eligibility.groovy`

**Logic:**
1. Reads `_global.fetch_order_oxford.units` and `targetUnitIds`
2. For each target unit, checks `unitChangeActions` for `actionType == "POST_DISPATCH_CHANGE_ADDRESS"` with `eligibility == true`
3. Collects eligible unit IDs
4. Extracts `toParty.deliveryAddressId` from first eligible unit (all units share same delivery address)

**Output:**
```json
{
  "eligibleUnitIds": ["436921306442895100000"],
  "deliveryAddressId": "CNTCT1E7827EC63A94B8D84673A982",
  "isEligible": true
}
```

**Output stored at:** `_global.check_minions_eligibility`

---

### 3. `branch_minions_eligible` (BRANCH)

**Rule:** `_global.check_minions_eligibility?.isEligible == true`
- `true` → `chore_eligibility`
- default → `not_eligible_failure`

---

### 4. `chore_eligibility` (HTTP)

**API:** `POST {chore.host}/api/v3/eligibility/addressChangeEligibility`

**New client required:** `chore` (targetClientId)

**Request body:**
```json
{
  "orderVersion": -1,
  "orderId": "<_global.orderDetails[0].orderId>",
  "orderItemUnitIds": ["<from check_minions_eligibility.eligibleUnitIds>"]
}
```

**Transformer:** `forward_address_phone_change/transform_chore_eligibility.groovy`

**Output:**
```json
{
  "choreId": "CU103696483280830262",
  "actionableUnitIds": ["436921306442895100000"],
  "isEligible": true
}
```

**Output stored at:** `_global.chore_eligibility`

---

### 5. `branch_chore_eligible` (BRANCH)

**Rule:** `_global.chore_eligibility?.isEligible == true`
- `true` → `get_current_address`
- default → `not_eligible_failure`

---

### 6. `get_current_address` (HTTP)

**API:** `GET {user_svc.host}/contacts/{customerId}/{deliveryAddressId}`

**New client required:** `user_svc` (targetClientId)

**URL params:**
- `customerId`: `_global.customer.customerId`
- `deliveryAddressId`: `_global.check_minions_eligibility.deliveryAddressId`

**Transformer:** `forward_address_phone_change/transform_current_address.groovy`

**Output:**
```json
{
  "id": "CNTCT1E7827EC63A94B8D84673A982",
  "name": "John Doe",
  "phone": "9876543210",
  "alt_phone": "9876543211",
  "pincode": "226017",
  "addressLine1": "123 Main Street",
  "addressLine2": "Apt 4B",
  "city": "Lucknow",
  "state": "Uttar Pradesh",
  "country": "India",
  "landmark": "Near Park",
  "address_location_tag": "HOME"
}
```

**Output stored at:** `_global.get_current_address`

---

### 7. `chore_feasibility` (HTTP)

**API:** `POST {chore.host}/api/v3/order/changeAddressFeasibility/v2`

**Key design:** `newPincode == oldPincode` because only the phone is changing — no address change.

**Request body:** `forward_address_phone_change/build_chore_feasibility_body.groovy`
```json
{
  "choreId": "<chore_eligibility.choreId>",
  "orderVersion": -1,
  "orderId": "<orderId>",
  "orderItemUnitIds": ["<chore_eligibility.actionableUnitIds>"],
  "newPincode": "<get_current_address.pincode>",
  "oldPincode": "<get_current_address.pincode>"
}
```

**Transformer:** `forward_address_phone_change/transform_chore_feasibility.groovy`

**Output:**
```json
{
  "isFeasible": true,
  "choreId": "CU103696483280830262"
}
```

**Output stored at:** `_global.chore_feasibility`

---

### 8. `branch_feasibility` (BRANCH)

**Rule:** `_global.chore_feasibility?.isFeasible == true`
- `true` → `ask_alternate_phone`
- default → `not_feasible_failure`

---

### 9. `ask_alternate_phone` (INSTRUCTION)

**Purpose:** Pause workflow. Display current address and phone info to agent/customer. Collect:
- `newContact` — new primary phone number for delivery
- `altContact` — alternate phone number

**Resume payload (from client):**
```json
{
  "newContact": "9123456789",
  "altContact": "9123456780"
}
```

**Resume values stored at:** `_global['ask_alternate_phone:viewResponse'].selectedOptions`
- `newContact`: `_global['ask_alternate_phone:viewResponse'].selectedOptions.newContact`
- `altContact`: `_global['ask_alternate_phone:viewResponse'].selectedOptions.altContact`

**Output stored at:** `_global.ask_alternate_phone` (instruction display data)

---

### 10. `create_new_address` (HTTP)

**API:** `POST {user_svc.host}/contacts/{customerId}`

**Purpose:** Creates a new User Service contact that copies all physical address fields from the current address but replaces phone and alt_phone with the new values from the INSTRUCTION resume.

This mirrors `getNewAddressWithAltNum` + `createContactInfo` in the action-server.

**Request body:** `forward_address_phone_change/build_create_address_body.groovy`

Built from:
- Physical fields: `_global.get_current_address` (name, addressLine1, city, state, country, pincode, landmark, address_location_tag)
- New phone: `_global['ask_alternate_phone:viewResponse'].selectedOptions.newContact`
- New alt phone: `_global['ask_alternate_phone:viewResponse'].selectedOptions.altContact`

**Transformer:** `forward_address_phone_change/transform_create_address.groovy`

**Output:**
```json
{
  "newAddressId": "CNTCT2F8938FD74B05C9E95784B093"
}
```

**Output stored at:** `_global.create_new_address`

---

### 11. `chore_confirm_address` (HTTP)

**API:** `POST {chore.host}/api/v3/order/changeAddressConfirm/v2`

**Purpose:** Confirms the address change with Chore service using the new contact ID.

**`fieldsUpdated`:** `TEXT_AND_PHONE_NUMBER` — since we create a new contact with same physical address but new phone, technically only phone changed, but using `TEXT_AND_PHONE_NUMBER` matches the action-server behavior for safety.

**`shouldUnhold`:** `false` — COD address verification edge case not implemented in V1.

**Request body:** `forward_address_phone_change/build_chore_confirm_body.groovy`
```json
{
  "choreId": "<chore_feasibility.choreId>",
  "orderVersion": -1,
  "orderId": "<orderId>",
  "orderItemUnitIds": ["<chore_eligibility.actionableUnitIds>"],
  "newPincode": "<get_current_address.pincode>",
  "oldPincode": "<get_current_address.pincode>",
  "addressId": "<create_new_address.newAddressId>",
  "userLogin": "fk_selfServe",
  "shouldUnhold": false,
  "fieldsUpdated": "TEXT_AND_PHONE_NUMBER"
}
```

**Transformer:** `forward_address_phone_change/transform_chore_confirm.groovy`

**Output:**
```json
{
  "choreId": "CU103696483280830262",
  "status": "SUCCESS",
  "actionableUnitToPromiseMap": { "436921306442895100000": 1773340199000 }
}
```

**Output stored at:** `_global.chore_confirm_address`

---

### 12. `fap_workflow_success` (SUCCESS)

Terminal node on successful completion.

### 13. `not_eligible_failure` (FAILURE)

Terminal node when Minions or Chore eligibility fails. Error: `"Unit not eligible for address change"`

### 14. `not_feasible_failure` (FAILURE)

Terminal node when Chore feasibility fails. Error: `"Address change not feasible for current pincode"`

---

## Data Field Mapping

### Oxford → Workflow context

| Oxford path | Drift field | Used by |
|------------|-------------|---------|
| `units.<id>.toParty.deliveryAddressId` | `check_minions_eligibility.deliveryAddressId` | `get_current_address` URL |
| `units.<id>.unitChangeActions` | eligibility filter | `check_minions_eligibility` logic |
| `units.<id>.chores` | (available if needed) | future: pending chore check |

### User Service → Workflow context

| User Service field | Drift field | Used by |
|-------------------|-------------|---------|
| `address.pincode.input` | `get_current_address.pincode` | `chore_feasibility`, `chore_confirm_address` |
| `communications[mobile].input` | `get_current_address.phone` | `create_new_address` (display) |
| `communications[alt_phone].input` | `get_current_address.alt_phone` | `create_new_address` (display) |
| `address.addressLine1.input` | `get_current_address.addressLine1` | `create_new_address` body |
| `id` | `get_current_address.id` | reference |

### INSTRUCTION resume → Workflow context

| Resume field | Drift field | Used by |
|-------------|-------------|---------|
| `newContact` | via `_global['ask_alternate_phone:viewResponse'].selectedOptions.newContact` | `create_new_address` body |
| `altContact` | via `_global['ask_alternate_phone:viewResponse'].selectedOptions.altContact` | `create_new_address` body |

---

## Clients to Register in Drift

| Client ID | Service | Endpoints used | Auth type |
|-----------|---------|---------------|-----------|
| `chore` | Chore Service | Eligibility, Feasibility, Confirm | Bearer token via `targetClientId` |
| `user_svc` | User Service | GET /contacts, POST /contacts | Bearer token via `targetClientId` |

Enum store keys expected:
- `chore.host` — Chore service base URL (via `_enum_store.clients.get('chore.host')`)
- `user_svc.host` — User Service base URL (via `_enum_store.clients.get('user_svc.host')`)

---

## What the Action-Server Does That We Skip (Phone-Only Flow)

| Action-Server logic | Drift decision |
|--------------------|----------------|
| `getSavedCustomerAddresses` | SKIPPED — no address selection UI needed |
| Complex grouping logic (SHIPMENT_ID, VAS units) | SIMPLIFIED — basic actionable unit check only |
| FBF / serviceProfile checks | SKIPPED |
| SFC enriched data | SKIPPED |
| Zulu data | SKIPPED |
| Return Service eligibility batch | SKIPPED |
| `pendingCODAddressVerification` / `shouldUnhold` | DEFERRED — hardcoded to `false` in V1 |
| `getPendingAddressChangeRequest` | DEFERRED — can be added if needed |
| GeoVendor `getPinCodeDetails` | DEFERRED — User Service called with raw address fields |

---

## Open Questions / V2 Enhancements

1. **`shouldUnhold`**: If a unit is in `APPROVAL_HOLD` with COD address verification, Chore needs `shouldUnhold: true`. Requires AE service call. Deferred to V2.
2. **Pending address change check**: Should we block if an active ADDRESS_CHANGE chore already exists? Available in Oxford (`units.<id>.chores`). Deferred to V2.
3. **GeoVendor**: User Service may require geo-enriched pincode data (city/state from geo). If createContact fails due to incomplete address, add a `geo_vendor` HTTP node before `create_new_address`.
4. **`fieldsUpdated` precision**: Currently hardcoded to `TEXT_AND_PHONE_NUMBER`. Could be made dynamic — `PHONE_NUMBER` if only phone changes, `TEXT_AND_PHONE_NUMBER` if text also differs.
