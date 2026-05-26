# Config / Enum Store Changes for forward_address_phone_change_smart

## lookup.properties additions

Add the following lines to register `forward_address_phone_change` as a V3 child workflow so the `update_incident` node's `build_update_incident_body.groovy` script can build the `v3ChildWorkflowRequest` correctly:

```properties
global.childWorkflow.forward_address_phone_change.isSmart=true
global.childWorkflow.forward_address_phone_change.actionEligibility=ADD_ALTERNATE_CONTACT
```

## How these keys are used

The generic `build_update_incident_body.groovy` script (at `src/main/resources/scripts/generic/build_update_incident_body.groovy`) reads:

```groovy
def workflowNameStr = workflowName.toString().trim()           // "forward_address_phone_change"
def workflowConfig = _enum_store?.childWorkflow?.get(workflowNameStr)
def isSmartWorkflow = Boolean.valueOf(workflowConfig?.isSmart?.toString()) // true
def actionEligibility = workflowConfig?.actionEligibility      // "ADD_ALTERNATE_CONTACT"
```

These drive the `v3ChildWorkflowRequest` payload sent to `updateIncidentV3`:

```json
{
  "v3ChildWorkflowRequest": {
    "workflowId": "<temporal_workflow_run_id>",
    "workflowName": "forward_address_phone_change",
    "workflowVersion": "SNAPSHOT",
    "isSmartWorkflow": true,
    "actionEligibility": "ADD_ALTERNATE_CONTACT"
  }
}
```

## Smart workflow state that uses this config

In `forward_address_phone_change_smart`, the `update_incident` state passes:

```json
"parameters": {
    "incidentId": "$.incidentId",
    "addChildWorkflow": "true"
}
```

The script derives `workflowName` from `_global.params.workflowId` (= `"forward_address_phone_change"`, set by the IMS controller when starting the smart workflow). `_global.workflowId` is the Temporal run ID. `_global.params.version` is `"SNAPSHOT"`.
