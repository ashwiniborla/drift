# Elixir Waiting For Response

## Overview
- **ID**: `elixir_waiting_for_response`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: WAITING state until the Elixir callback resumes the workflow. The screen shows post-fulfillment / tracking data via template `elixir_ticket_details` and tag `imsv2.info`, populated dynamically from `elixir_get_order_details.postFulfillmentData` by node `prepare_elixir_waiting_for_response_display`.

## Parameters

None.

## Script Details

### inputOptions (dynamic)
- **Location**: Built at runtime by `prepare_elixir_waiting_for_response_display.groovy`
- **possibleDynamicValues**: `prepare_elixir_waiting_for_response_display.inputOptions`
- **Logic summary**: First option’s `possibleDynamicValues` resolves to the full `List<Option>` returned by the GROOVY node (see `InstructionNodeActivityImpl.resolveOptions`).

## Resume and fields for groovy script

Workflow is resumed when the callback API receives a request with action_type `ELIXIR_TICKET_UPDATE`. The resume payload must set **selectedOptions** with these keys (used by `prepare_elixir_response_update.groovy`):

| selectedOptions key | Type    | Required | Description |
|---------------------|---------|----------|-------------|
| `status`            | String  | Yes      | CREATED or REJECTED |
| `ticket_id`         | String  | No       | Elixir ticket ID; null when REJECTED |
| `created_at`        | String  | Yes      | Timestamp from callback (used as updatedAt in elxrTkt) |
| `message`           | String  | No       | Optional message (e.g. rejection reason) |

See `resume_request.md` for sample payloads.

## Output
- **Returns**: N/A until resume. After resume, viewResponse is available at `_global.get('elixir_waiting_for_response:viewResponse')`.

## Dependencies
- **Expected previous node**: `prepare_elixir_waiting_for_response_display` (must run immediately before this instruction)
- **Data dependency**: `elixir_get_order_details.postFulfillmentData` must exist before the preparer runs

## Notes
- Register node `prepare_elixir_waiting_for_response_display` in the workflow and set its `instanceName` to `prepare_elixir_waiting_for_response_display`.
- Sync `node.json` definitions to the Drift API/store (e.g. sync script) after changes.
