# Update Incident (update_incident_v3)

## Overview

- **Node ID**: `update_incident_v3`
- **Type**: HTTP
- **Purpose**: Calls IMS updateIncidentV3 API. Body is built by the generic script `scripts/generic/build_update_incident_body.groovy`. **addV3ChildWorkflow** is added when `nodeParameters.addChildWorkflow` is true (build from _global + _enum_store) or when `nodeParameters.childWorkflowDetails` is present (object).

## Body script

- **Location**: `worker/src/main/resources/scripts/generic/build_update_incident_body.groovy`
- **Context**: `_global`, `_global.nodeParameters`, `_global.params`, `_global.workflowId`, `_enum_store`

## Parameters (nodeParameters)

| Parameter | Required | Description |
|-----------|----------|-------------|
| `incidentId` | yes | Incident ID to update. |
| `addChildWorkflow` | no | When **true**, script builds v3ChildWorkflowRequest from _global.workflowId, _global.params.workflowId/version, _enum_store (see below). Pass as static param in workflow (e.g. true) for create_elixir_ticket_workflow. |
| `statusWithType` | no | `{ id, name }`; alternative to `statusId`. |
| `statusId` | no | Status ID when `statusWithType` is null. |
| `questionnaireData` | no | Added as `customFields.qsnareDta`. |
| `elxrTkt` | no | Elixir ticket details in `customFields.elxrTkt`. |
| `threads` | no | List of incident thread request maps. |
| `notesText` | no | When `threads` is null, builds one thread with this text. |
| `childWorkflowDetails` | no | When present (and addChildWorkflow is not true), adds v3ChildWorkflowRequest from this object. |

## addV3ChildWorkflow when `addChildWorkflow` is true

Script builds `v3ChildWorkflowRequest` from:

| Field | Source | Validation / default |
|-------|--------|----------------------|
| workflowId | `_global.workflowId` | Required; throw if null or blank. |
| workflowName | `_global.params.workflowId` | Required; throw if null or blank. |
| workflowVersion | `_global.params.version` | Required; throw if null or blank. |
| isSmartWorkflow | `_enum_store.get('childWorkflow.<workflowName>.isSmart')` | **false** if not configured. |
| actionEligibility | `_enum_store.get('childWorkflow.<workflowName>.actionEligibility')` | **blank** → sent as null if not configured. |

`<workflowName>` = `_global.params.workflowId`. Enum keys have no `global.` prefix.

## addV3ChildWorkflow when `childWorkflowDetails` is present

When `addChildWorkflow` is not true but `childWorkflowDetails` is non-null, all fields are read from that object (workflowId, workflowName, workflowVersion, isSmartWorkflow, actionEligibility).

## Output

- **Returns**: HTTP response; transformer (if any) extracts fields into `_global.<instanceName>`.

## Dependencies

- **Reads**: `nodeParameters.*`, `_global.params`, `_global.workflowId`, `_enum_store` (when addChildWorkflow true)
- **Expected**: IMS base URL from config (e.g. `global.ims.baseUrl`).

## Notes

- In create_elixir_ticket_workflow: add **update incident** as first workflow node `register_elixir_child_workflow` and pass `addChildWorkflow: true` (and ensure `params.workflowId`, `params.version` and `workflowId` are set when the workflow runs).
