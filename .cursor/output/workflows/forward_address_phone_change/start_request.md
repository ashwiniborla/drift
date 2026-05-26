# Start Request — forward_address_phone_change

## Overview
- **Workflow ID**: `forward_address_phone_change`
- **Purpose**: Triggered when a customer service agent initiates an alternate phone number update for a forward delivery order. The workflow validates eligibility and feasibility before collecting new phone numbers from the agent.

---

## Request payload / parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `incidentId` | String | yes | IMS incident ID associated with the request |
| `customer.customerId` | String | yes | Customer's unique identifier (used for User Service calls) |
| `orderDetails[0].orderId` | String | yes | FK order ID (e.g. `OD436921306442895100`) |
| `orderDetails[0].orderItemUnitIds` | String[] | yes | Unit IDs to process (e.g. `["436921306442895100000"]`) |
| `threadContext.perfFlag` | String | no | Perf test flag, passed as `X-PERF-TEST` header to all downstream calls. Defaults to `"false"`. |

---

## Sample request

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

## Workflow execution path

```
fetch_order_oxford
  → check_minions_eligibility
    → branch_minions_eligible
      [eligible] → chore_eligibility
        → branch_chore_eligible
          [eligible] → get_current_address
            → chore_feasibility
              → branch_feasibility
                [feasible] → ask_alternate_phone  ← WAITING (agent input)
                  → create_new_address
                    → chore_confirm_address
                      → fap_workflow_success  ✓

      [any failure path] → default_failure  ✗
```

---

## Notes
- The workflow checks eligibility and feasibility *before* pausing for agent input — no phone numbers are collected unless the change is confirmed as feasible
- Only the phone numbers change; the physical address and pincode remain unchanged
- Requires `chore` and `user_svc` clients registered in `_enum_store.clients`
