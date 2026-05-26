# Create Elixir Ticket Workflow

## Overview
- **ID**: `create_elixir_ticket_workflow`
- **Version**: `1`
- **Start node**: `register_elixir_child_workflow`
- **Default failure node**: `default_failure`
- **Comment**: Workflow for creating elixir tickets. Registers the child workflow, fetches order from Oxford (shared `e2e_fetch_order_details` HTTP node), extracts post-fulfillment data via Groovy transformation, and branches on `allowed`; when allowed, calls IMS incidents filter; branches on filter count (count > 0 → elixir_waiting_for_response, else → elixir_create_ticket).

## States

| State | Resource ID | Next / branch | Terminal | Parameters |
|-------|-------------|----------------|----------|-------------|
| register_elixir_child_workflow | update_incident | elixir_fetch_order_oxford | false | incidentId, childWorkflowAction, workflowId, ... |
| elixir_fetch_order_oxford | e2e_fetch_order_details | elixir_slice_order_from_oxford | false | dataVariable, useCase; contextOverrideKey: fetch_order_oxford |
| elixir_slice_order_from_oxford | elixir_slice_order_from_oxford | elixir_check_allowed | false | dataVariable; contextOverrideKey: elixir_get_order_details |
| elixir_check_allowed | elixir_check_allowed | allowed=true → elixir_filter_incidents; default → default_failure | false | — |
| elixir_filter_incidents | elixir_filter_incidents | elixir_check_filter_count | false | tenant, issueId |
| elixir_check_filter_count | elixir_check_filter_count | BRANCH: see node (e.g. elixir_create_ticket, update_incident_elixir_requested, …) | false | — |
| update_incident_elixir_requested | update_incident | prepare_elixir_waiting_for_response_display | false | incidentId, elxrTkt, notesText |
| prepare_elixir_waiting_for_response_display | prepare_elixir_waiting_for_response_display | elixir_waiting_for_response | false | — |
| elixir_waiting_for_response | elixir_waiting_for_response | prepare_elixir_response_update | false | — |
| elixir_incidents_found_end | elixir_incidents_found_end | — | true | — |
| default_failure | default_failure | — | true | — |

## Flow

1. **register_elixir_child_workflow**: Registers this workflow as a child on the incident via `update_incident`. Then → **elixir_fetch_order_oxford**.
2. **elixir_fetch_order_oxford**: Calls Oxford (`e2e_fetch_order_details`, passthrough transformer). Raw response stored at `_global.fetch_order_oxford` (via `contextOverrideKey`). Then → **elixir_slice_order_from_oxford**.
3. **elixir_slice_order_from_oxford**: GROOVY node; reads `_global.fetch_order_oxford`, extracts `itemType`, `postFulfillmentData`, `allowed` for the unit matching `trackingId`. Output stored at `_global.elixir_get_order_details` (via `contextOverrideKey`). Then → **elixir_check_allowed**.
4. **elixir_check_allowed**: If `allowed` true → **elixir_filter_incidents**; else → **default_failure**.
5. **elixir_filter_incidents**: POST to IMS `/incidents/filter`. Output in `_global.elixir_filter_incidents` (includes `count`). Then → **elixir_check_filter_count**.
6. **elixir_check_filter_count**: Branch on `elixir_filter_incidents.action` (see `elixir_check_filter_count` BRANCH node), e.g. create ticket vs wait for acceptance vs listen to updates.
7. **`update_incident_elixir_requested`** (when branching to wait for acceptance): Updates incident with REQUESTED elxrTkt, then → **prepare_elixir_waiting_for_response_display** → **elixir_waiting_for_response**.
8. **prepare_elixir_waiting_for_response_display**: GROOVY; builds `inputOptions` for the instruction from `elixir_get_order_details.postFulfillmentData`. Then → **elixir_waiting_for_response**.
9. **elixir_waiting_for_response**: INSTRUCTION (WAITING); shows `elixir_ticket_details` / `imsv2.info`; resumes on Elixir callback. Then → **prepare_elixir_response_update**.
10. **elixir_incidents_found_end**: Terminal success (used by other branches if needed).
11. **default_failure**: Terminal failure / end.

## Notes
- Triggered by parent (child workflow) or called by parent. Start payload: `issueDetail`, `orderDetails`, `params` (include `tenant`, `issueId` for filter step).
- The `elixir_get_order_details` **HTTP node** is no longer used; the Oxford fetch is now shared via `e2e_fetch_order_details` followed by the `elixir_slice_order_from_oxford` GROOVY node. Downstream JsonPaths (`$.elixir_get_order_details.*`) are unchanged because the GROOVY node writes to the same context key via `contextOverrideKey: elixir_get_order_details`.
- When filter count > 0, workflow goes to **elixir_waiting_for_response** (no new ticket created; waits for callback). When count == 0, workflow goes to **elixir_create_ticket**.
- Oxford URL and IMS base URL are in enum store under `clients`.
