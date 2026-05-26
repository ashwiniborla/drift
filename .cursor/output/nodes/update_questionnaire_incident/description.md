# update_questionnaire_incident

## Overview
- **ID**: `update_questionnaire_incident`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Calls the updateIncidentV3 API (`POST /incidents/v3/{incidentId}`) to mark the questionnaire incident as solved (statusId = 2) and attach `questionnaireData` in customFields.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `incidentId` | The incident ID to update | `$.create_questionnaire_incident.incidentId` |
| `questionnaireData` | Questionnaire data (type, response, createdAt) | `$.validate_questionnaire_response.questionnaireData` |

## Script Details

### url (in-node)
- **Location**: embedded
- **Context access**: `_global.nodeParameters.incidentId`
- **Logic summary**: Returns `http://localhost:5500/incidents/v3/{incidentId}`

### headers (in-node)
- **Location**: embedded
- **Context access**: `_global.threadContext.perfFlag`
- **Logic summary**: Same header set as create: Content-Type, x_ims_channel, x_ims_username, x_ims_tenant, x_ims_client_id, x_perf_test.

### body (dynamic)
- **Location**: `worker/src/main/resources/scripts/fake_questionnaire/build_update_incident_body.groovy`
- **Context access**: `_global.nodeParameters.questionnaireData`
- **Logic summary**: Builds IncidentRequest with statusId=2 (solved) and customFields containing questionnaireData ({ questionnaireType: FAKE_FORWARD/FAKE_REVERSE, response, createdAt }).

### transformer (in-node)
- **Location**: embedded
- **Logic summary**: Passes through the full API response.

## Output
- **Returns**: Map stored in `_global.update_questionnaire_incident` — the full IncidentResponse from the API.

## Dependencies
- **Reads from _global**: `nodeParameters.incidentId`, `nodeParameters.questionnaireData`, `threadContext.perfFlag`
- **Expected previous nodes**: `create_questionnaire_incident` (provides incidentId), `validate_questionnaire_response` (provides questionnaireData)

## Notes
- **targetClientId**: `imsv2_varadhi_client1`
- URL is currently hardcoded to `localhost:5500`. Update to use `_enum_store["downstream_ips"]` when configured.
- statusId `2` = Solved.
- `questionnaireType` is `FAKE_FORWARD` for forward flow, `FAKE_REVERSE` for reverse flow.
