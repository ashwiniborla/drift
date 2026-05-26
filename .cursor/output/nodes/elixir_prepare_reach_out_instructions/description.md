# elixir_prepare_reach_out_instructions

## Overview

- **ID**: `elixir_prepare_reach_out_instructions`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: After `prepare_elixir_action_update` / `update_incident_elixir_action` on the **OTHERS** branch, prepares payload and headers for `push_to_varadhi` to publish a reach-out event to a Varadhi **topic** (`reachout.elixirCommunicationTopic`).

## Parameters

None (reads from `_global` and `_enum_store` only).

## Script

- **Location**: `worker/src/main/resources/scripts/create_elixir_ticket_workflow/elixir_prepare_reach_out_instructions.groovy`
- **Logic summary**:
  - Resolves `elixir_waiting_for_updates:viewResponse.selectedOptions.context` for `reason_code`, `subreason_code`, `persona`.
  - Builds composite `reasonSubReasonKey` as `reasonCode` or `reasonCode_subreasonCode` when subreason is non-empty (same rule as previously in `prepare_elixir_action_update`).
  - Sets `extraHeaders`: `X-TENANT-ID` = `imsv3-worker`, `X_REASON_SUBREASON` = composite key, `X_EVENT_NAME` = `ELIXIR_ACTIONABLE_EVENT` if `prepare_elixir_action_update.action == 'ALT_PH_NUMBER_REQUIRED'`, else `ELIXIR_NONACTIONABLE_EVENT`.
  - Topic name: `_enum_store.get('reachout.elixirCommunicationTopic')` → output field `topicName`.
  - Body: `account_id` (customer or Oxford fallback), `order_id`, `unit_id`, `trackingId` from `orderDetails[0]`; `action` from `prepare_elixir_action_update.action`; `reason` / `subreason` from context codes; `persona` from context.

## Output

Returns a map consumed by the next workflow state (`push_to_varadhi`):

| Field | Description |
|-------|-------------|
| `topicName` | Varadhi topic name (bound to `push_to_varadhi` `name`) |
| `body` | JSON map for Varadhi message body |
| `extraHeaders` | Additional headers merged by `push_to_varadhi` |
| `groupId` | `null` (no RESTBUS group) |
| `httpUri` | `null` |
| `method` | `POST` |

## Dependencies

- **Reads**: `_global.elixir_waiting_for_updates:viewResponse`, `_global.prepare_elixir_action_update`, `_global.customer`, `_global.orderDetails`, `_global.fetch_order_oxford`, `_enum_store`
- **Expected prior states**: `elixir_waiting_for_updates` resume, `prepare_elixir_action_update`, `update_incident_elixir_action`, `elixir_action_branch` → OTHERS → this node.

## Notes

- `reasonSubReasonKey` is **not** produced by `prepare_elixir_action_update` anymore; only this script builds it for reach-out.
