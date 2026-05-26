# Testing Plan: `forward_address_phone_change` Workflow

This document covers the end-to-end testing strategy for the alternate phone number update workflow including mock API payloads for positive and negative scenarios.

---

## Workflow Input (Start Parameters)

```json
{
  "incidentId": "INC123456",
  "customer": {
    "customerId": "CUST_9876543210"
  },
  "orderDetails": [
    {
      "orderId": "OD436921306442895100",
      "orderItemUnitIds": ["436921306442895100000"]
    }
  ],
  "threadContext": {
    "perfFlag": "false"
  }
}
```

---

## Node-by-Node Test Scenarios

---

### 1. `fetch_order_oxford` (Oxford API call)

**Purpose:** Fetch aggregated order details including units and `deliveryAddressId`.

#### Mock Request
```
POST /api/v2/aggregated-order-details
Body: { "orderId": "OD436921306442895100", "useCase": "default" }
```

#### Mock Response (Positive — Unit Eligible)
```json
{
  "resolvedVariablesResponse": {
    "v2OrderData_cs_controller_client_check_default": {
      "oms3_aggregated_order": {
        "orderId": "OD436921306442895100",
        "units": {
          "436921306442895100000": {
            "unitId": "436921306442895100000",
            "status": "IN_TRANSIT",
            "toParty": {
              "deliveryAddressId": "CNTCT_EXISTING_001"
            },
            "unitChangeActions": [
              {
                "actionType": "CHANGE_SECONDARY_PHONE_NUMBER",
                "eligibility": true
              }
            ]
          }
        }
      }
    }
  }
}
```

**Expected `_global.fetch_order_oxford`:**
```json
{
  "units": {
    "436921306442895100000": { ... }
  },
  "targetUnitIds": ["436921306442895100000"]
}
```

---

### 2. `check_minions_eligibility` (Groovy)

**Input:** `_global.fetch_order_oxford`

#### Positive Case
- Unit `436921306442895100000` has `POST_DISPATCH_CHANGE_ADDRESS` eligibility = true

**Expected output:**
```json
{
  "eligibleUnitIds": ["436921306442895100000"],
  "deliveryAddressId": "CNTCT_EXISTING_001",
  "isEligible": true
}
```

#### Negative Case (unit missing `CHANGE_SECONDARY_PHONE_NUMBER` action or `eligibility=false`)
**Expected output:**
```json
{
  "eligibleUnitIds": [],
  "deliveryAddressId": null,
  "isEligible": false
}
```
→ `branch_minions_eligible` routes to `default_failure`

---

### 3. `chore_eligibility` (Chore Service API)

#### Mock Request
```
POST /api/v3/eligibility/addressChangeEligibility
Headers: { "Content-Type": "application/json", "x-client-id": "self_serve" }
Body:
{
  "orderVersion": -1,
  "orderId": "OD436921306442895100",
  "orderItemUnitIds": ["436921306442895100000"]
}
```

#### Mock Response (Positive — Eligible)
```json
{
  "choreId": "CU103696483280830262",
  "status": "SUCCESS",
  "groupData": {
    "postDispatchActionableUnits": ["436921306442895100000"],
    "preDispatchActionableUnits": [],
    "nonActionableUnits": [],
    "linkedUnits": []
  },
  "unitEligibilityList": [
    {
      "unitId": "436921306442895100000",
      "eligibleOptions": ["CHANGE_ADDRESS_TEXT"]
    }
  ]
}
```

**Expected `_global.chore_eligibility`:**
```json
{
  "choreId": "CU103696483280830262",
  "actionableUnitIds": ["436921306442895100000"],
  "isEligible": true
}
```

#### Mock Response (Negative — Unit missing `CHANGE_ADDRESS_TEXT` option)
```json
{
  "choreId": "CU103696483280830263",
  "status": "SUCCESS",
  "groupData": {
    "postDispatchActionableUnits": [],
    "preDispatchActionableUnits": [],
    "nonActionableUnits": ["436921306442895100000"],
    "linkedUnits": []
  },
  "unitEligibilityList": [
    {
      "unitId": "436921306442895100000",
      "eligibleOptions": []
    }
  ]
}
```

**Expected `_global.chore_eligibility`:**
```json
{
  "choreId": "CU103696483280830263",
  "actionableUnitIds": [],
  "isEligible": false
}
```
→ `branch_chore_eligible` routes to `default_failure`

---

### 4. `get_current_address` (User Service GET)

#### Mock Request
```
GET /contacts/CUST_9876543210/CNTCT_EXISTING_001
```

#### Mock Response (Positive)
```json
{
  "id": "CNTCT_EXISTING_001",
  "name": { "input": "John Doe" },
  "address": {
    "addressType": "user_generated",
    "addressLine1": { "input": "123 Main Street" },
    "addressLine2": { "input": "Apartment 4B" },
    "city":    { "input": "Lucknow" },
    "state":   { "input": "Uttar Pradesh" },
    "country": { "input": "India" },
    "pincode": { "input": "226017" },
    "landmark": { "input": "Near City Mall" },
    "locationTypeTag": "HOME"
  },
  "communications": [
    { "type": "mobile",    "input": "9876543210" },
    { "type": "alt_phone", "input": "9876500000" }
  ]
}
```

**Expected `_global.get_current_address`:**
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

---

### 5. `chore_feasibility` (Chore Service API)

#### Mock Request
```
POST /api/v3/order/changeAddressFeasibility/v2
Body:
{
  "choreId": "CU103696483280830262",
  "orderVersion": -1,
  "orderId": "OD436921306442895100",
  "orderItemUnitIds": ["436921306442895100000"],
  "newPincode": "226017",
  "oldPincode": "226017"
}
```

> **Note:** `newPincode` == `oldPincode` because only phone is changing, not the physical address.

#### Mock Response (Positive — Feasible)
```json
{
  "actionable_units": [
    {
      "order_item_unit": "436921306442895100000",
      "change_promise_options": {
        "new_promise_date": 1773340199000,
        "new_speed_tier": "REGULAR"
      }
    }
  ],
  "non_actionable_units": [],
  "un_processed_units": [],
  "linkages": []
}
```

**Expected `_global.chore_feasibility`:**
```json
{
  "actionableUnitIds": ["436921306442895100000"],
  "nonActionableUnitIds": [],
  "isFeasible": true
}
```

#### Mock Response (Negative — Not Feasible)
```json
{
  "actionable_units": [],
  "non_actionable_units": [
    {
      "order_item_unit": "436921306442895100000",
      "reason": "UNIT_IN_NON_SERVICEABLE_AREA"
    }
  ],
  "un_processed_units": [],
  "linkages": []
}
```

**Expected `_global.chore_feasibility`:**
```json
{
  "actionableUnitIds": [],
  "nonActionableUnitIds": ["436921306442895100000"],
  "isFeasible": false
}
```
→ `branch_feasibility` routes to `default_failure`

> **Note:** Feasibility is determined by `nonActionableUnits` being empty — even if `actionable_units` is partially populated, the presence of any non-actionable unit marks the check as not feasible.

---

### 6. `ask_alternate_phone` (INSTRUCTION node — User Input)

**Workflow pauses here.** Agent sees current address details and collects new phone numbers.

#### Resume Payload (Positive)
```json
{
  "newContact": "9999988888",
  "altContact": "9999977777",
  "action": "CONFIRM"
}
```

#### Resume Payload (Cancel)
```json
{
  "action": "CANCEL"
}
```

> If `action == CANCEL`, the workflow should be designed to route to `default_failure` or a cancellation state (can be added as a branch after `ask_alternate_phone` if needed).

---

### 7. `create_new_address` (User Service POST)

#### Mock Request
```
POST /contacts/CUST_9876543210
Body:
{
  "name": { "input": "John Doe" },
  "address": {
    "addressType": "user_generated",
    "addressLine1": { "input": "123 Main Street" },
    "addressLine2": { "input": "Apartment 4B" },
    "city":    { "input": "Lucknow" },
    "state":   { "input": "Uttar Pradesh" },
    "country": { "input": "India" },
    "pincode": { "input": "226017" },
    "landmark": { "input": "Near City Mall" },
    "locationTypeTag": "HOME"
  },
  "communications": [
    { "type": "mobile",    "input": "9999988888" },
    { "type": "alt_phone", "input": "9999977777" }
  ]
}
```

#### Mock Response (Positive)
```json
{
  "id": "CNTCT_NEW_789ABC"
}
```

**Expected `_global.create_new_address`:**
```json
{
  "newAddressId": "CNTCT_NEW_789ABC"
}
```

#### Mock Response (Negative — Error)
```json
{
  "error": "INVALID_PHONE_NUMBER",
  "message": "Phone number format is invalid"
}
```
→ Node throws exception → workflow routes to `default_failure`

---

### 8. `chore_confirm_address` (Chore Service API)

#### Mock Request
```
POST /api/v3/order/changeAddressConfirm/v2
Body:
{
  "choreId": "CU103696483280830262",
  "orderVersion": -1,
  "orderId": "OD436921306442895100",
  "orderItemUnitIds": ["436921306442895100000"],
  "newPincode": "226017",
  "oldPincode": "226017",
  "addressId": "CNTCT_NEW_789ABC",
  "userLogin": "fk_selfServe",
  "shouldUnhold": false,
  "fieldsUpdated": "TEXT_AND_PHONE_NUMBER"
}
```

#### Mock Response (Positive)
```json
{
  "choreId": "CU103696483280830262",
  "status": "SUCCESS",
  "actionableUnitToPromiseMap": {
    "436921306442895100000": 1773340199000
  },
  "nonActionableUnitToReasonMap": {},
  "linkedUnits": []
}
```

**Expected `_global.chore_confirm_address`:**
```json
{
  "choreId": "CU103696483280830262",
  "status": "SUCCESS",
  "isSuccess": true
}
```
→ workflow proceeds to `fap_workflow_success`

#### Mock Response (Negative)
```json
{
  "choreId": "CU103696483280830262",
  "status": "FAILURE",
  "actionableUnitToPromiseMap": {},
  "nonActionableUnitToReasonMap": {
    "436921306442895100000": "UNIT_LOCKED"
  }
}
```
→ transformer throws exception → workflow routes to `default_failure`

---

## Test Scenarios Summary

| Scenario | Failing Step | Expected Terminal Node |
|----------|-------------|------------------------|
| Happy path — all checks pass, phone updated | — | `fap_workflow_success` |
| Minions eligibility fails (`eligibility=false`) | `check_minions_eligibility` / `branch_minions_eligible` | `default_failure` |
| Chore eligibility fails (unit in nonActionableUnits) | `chore_eligibility` / `branch_chore_eligible` | `default_failure` |
| Chore feasibility fails (unit in non_actionable_units) | `chore_feasibility` / `branch_feasibility` | `default_failure` |
| User Service returns no contact ID | `create_new_address` | `default_failure` |
| Chore confirm returns FAILURE status | `chore_confirm_address` | `default_failure` |

---

## Validation Checklist

- [ ] Oxford response correctly parsed; `deliveryAddressId` extracted
- [ ] Minions `POST_DISPATCH_CHANGE_ADDRESS` action type matched exactly
- [ ] Chore eligibility `choreId` is passed through to feasibility and confirm
- [ ] `newPincode == oldPincode` in feasibility and confirm requests
- [ ] `fieldsUpdated = "TEXT_AND_PHONE_NUMBER"` in confirm request
- [ ] New contact created with existing physical address + new phone numbers
- [ ] `altContact` is optional — omitted when empty
- [ ] `default_failure` node is reached on all error paths
