# Fetch Location Workflow

## Overview
- **ID**: `fetch_location`
- **Version**: `1`
- **Start node**: `fetch_location_fetch_oxford`
- **Default failure node**: `default_failure`
- **Comment**: Evaluates the customer's delivery location via AIS. If a nudge is required, shows a location confirmation screen with a deeplink for the customer to share their precise location. Used as a subworkflow in questionnaire_workflow to replace the collect_location stub.

## States

| State | Resource ID | Instance Name | Next Node | Terminal | Parameters |
|-------|-------------|---------------|-----------|----------|------------|
| `fetch_location_fetch_oxford` | `e2e_fetch_order_details` | `fetch_order_oxford` | `evaluate_location` | false | `dataVariable`, `useCase` |
| `evaluate_location` | `evaluate_location` | `evaluate_location` | `branch_nudge_required` | false | — |
| `branch_nudge_required` | `branch_nudge_required` | `branch_nudge_required` | (branch) | false | — |
| `prepare_location_instruction` | `prepare_location_instruction` | `prepare_location_instruction` | `show_location_instruction` | false | — |
| `show_location_instruction` | `show_location_instruction` | `show_location_instruction` | `fetch_location_success` | false | `inputOptions` |
| `fetch_location_success` | `fetch_location_success` | `fetch_location_success` | — | true | — |
| `default_failure` | `default_failure` | `default_failure` | — | true | — |

## Flow

```
fetch_location_fetch_oxford
  → evaluate_location
  → branch_nudge_required
      ├─ [nudge.required == true]  → prepare_location_instruction → show_location_instruction → fetch_location_success
      ├─ [nudge.required == false] → fetch_location_success
      └─ [default]                 → default_failure
```

1. **fetch_location_fetch_oxford**: Fetches Oxford order data into `_global.fetch_order_oxford` (context override — instanceName is `fetch_order_oxford`).
2. **evaluate_location**: Calls AIS `/api/v3/contact/location/evaluate` with `accountId` (from `_global.customer.customerId`) and `contactId` (from `_global.get_current_address.id`). Response stored as `_global.evaluate_location`.
3. **branch_nudge_required**: Routes on `_global.evaluate_location.nudge.required`.
4. **prepare_location_instruction**: Builds `inputOptions` array with address data + deepLink URL. State `parameters`: `showSuccess` → `$.chore_confirm_address.isSuccess` (script inverts JsonPath booleans for UI copy; see node description). If `chore_confirm_address` is absent in merged context, JsonPath resolves null and the script falls back to `_global.chore_confirm_address`.
5. **show_location_instruction**: INSTRUCTION node — displays the location confirmation screen with `workflowStatus: COMPLETED`. UI shows the address and a Submit button linking to the deepLink.
6. **fetch_location_success**: Terminal SUCCESS node (excluded when used as `sub_fetch_location` subworkflow with `includeLastNode: false`).

## Notes
- The `show_location_instruction` sets `workflowStatus: COMPLETED` — no resume is expected after the screen is shown.
- When inlined via `sub_fetch_location` (with `includeLastNode: false`), the flattener rewires both the nudge=false and nudge=true terminal paths to the parent workflow's `nextNode`.
- `get_current_address` data is expected to be present in the parent workflow context before this workflow runs.
