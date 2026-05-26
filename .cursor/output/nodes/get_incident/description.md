# get_incident

## Overview

- **ID**: `get_incident`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: `GET /incidents/{incidentId}` against IMS (`imsv2.ch.host`) with optional `includeV3SmartWorkflow` query flag. Response shaping is delegated to a Groovy file path passed as `transformerScript` (typically under `scripts/incident_extractor/`).

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `incidentId` | IMS incident external id | e.g. `$.params.incidentId` |
| `includeV3SmartWorkflow` | When true, adds `includeV3SmartWorkflow=true` to query; when absent/false, omitted (IMS default false) | literal or `$.params.includeV3SmartWorkflow` |
| `transformerScript` | Classpath-relative script path loaded with `new File(path).text` + `evaluate` | literal, e.g. `src/main/resources/scripts/incident_extractor/extract_elxr_tkt_from_get_incident.groovy` |

## Script Details

### url (in-node)

- **Context**: `_enum_store.clients['imsv2.ch.host']`, `_global.nodeParameters.incidentId`
- **Logic**: Concatenate base host with `/incidents/{incidentId}` (not `/incidents/v3/...`).

### headers (in-node)

- **Same pattern as** `update_incident` / `elixir_filter_incidents`: `x_perf_test`, `x_ims_client_id`, `x_ims_tenant`, `x_ims_username` from `_global.global_params.threadContext`, `Content-Type: application/json`.

### queryParams (in-node)

- Always sends `combined=true`. Adds `includeV3SmartWorkflow=true` only when the parameter is truthy.

### body (in-node)

- Returns `null` for GET.

### transformer (in-node loader)

- Requires non-empty `transformerScript`; loads file and `return evaluate(script)`.
- **Inner script** sees `_response` as the raw IMS GET JSON.

## Output

- **Returns**: Whatever the dynamic transformer script returns (stored under `_global.<instanceName>`).

## Dependencies

- **Reads from _enum_store**: `clients['imsv2.ch.host']`
- **Reads from _global**: `nodeParameters`, `global_params.threadContext`

## Notes

- **Downstream name / host**: `imsv2.ch.host` via `_enum_store.clients` (no hardcoded IP).
- **targetClientId**: `imsv2_varadhi_client1`, consistent with other IMS HTTP nodes.
