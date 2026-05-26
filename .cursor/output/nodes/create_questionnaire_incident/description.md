# create_questionnaire_incident

**Superseded by**: `check_similarity_and_create_incident` — use that node with parameters bound from workflow context. The questionnaire_workflow state key remains `create_questionnaire_incident` but uses `resourceId: "check_similarity_and_create_incident"`.

## Overview
- **ID**: `create_questionnaire_incident`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: (Legacy) Calls the createIncidentV3 API to create a questionnaire incident. Replaced by generic node `check_similarity_and_create_incident`.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | Body is built from `_global` directly | `_global.issueDetail`, `_global.queueDetail`, `_global.customer`, `_global.orderDetails` |

## Script Details

### url (in-node)
- **Location**: embedded
- **Logic summary**: Returns `http://localhost:5500/incidents/v3/`

### headers (in-node)
- **Location**: embedded
- **Context access**: `_global.threadContext.perfFlag`
- **Logic summary**: Sets Content-Type, x_ims_channel, x_ims_username, x_ims_tenant, x_ims_client_id, x_perf_test headers. perfFlag is dynamic from threadContext.

### body (dynamic)
- **Location**: `worker/src/main/resources/scripts/fake_questionnaire/build_create_incident_body.groovy`
- **Context access**: `_global.issueDetail`, `_global.queueDetail`, `_global.customer`, `_global.orderDetails`
- **Logic summary**: Builds a WorkflowStartRequest body from the top-level _global fields.

### transformer (in-node)
- **Location**: embedded
- **Logic summary**: Extracts `status` and `incidentId` from the API response.

## Output
- **Returns**: Map stored in `_global.create_questionnaire_incident`:
  - `status` (String — response status)
  - `incidentId` (String — the created incident ID)

## Dependencies
- **Reads from _global**: `issueDetail`, `queueDetail`, `customer`, `orderDetails`, `threadContext.perfFlag`
- **Expected previous nodes**: `validate_questionnaire_response`

## Notes
- **targetClientId**: `imsv2_varadhi_client1`
- URL is currently hardcoded to `localhost:5500`. Update to use `_enum_store["downstream_ips"]` when configured.
- The createIncidentV3 API returns `Map<String, String>` with key `status` containing the incident ID or status string.
