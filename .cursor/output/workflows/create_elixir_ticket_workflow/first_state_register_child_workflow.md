# create_elixir_ticket_workflow — First State: Register Child Workflow

## Add update_incident_v3 as first node

Add the **update_incident_v3** node as the **first** state in the workflow so the incident is updated with v3ChildWorkflowRequest (register child workflow) before any other steps.

## State configuration

- **State key / workflow node**: `register_elixir_child_workflow`
- **Resource**: `update_incident_v3` (node definition ID)
- **Resource version**: as per your node version (e.g. `"1"`)
- **Next node**: whatever was previously the first node in the workflow (or the next step after registration)

## Parameters (node parameters for this state)

| Parameter | Value | Description |
|-----------|--------|-------------|
| `incidentId` | e.g. `$.params.incidentId` or `$.issueDetail.issueId` | Incident ID to update (from workflow context). |
| `addChildWorkflow` | `true` | Static; enables building v3ChildWorkflowRequest from _global + _enum_store. |

## Workflow start / context requirements

When this node runs, the body script expects:

- `_global.workflowId` — set by runtime (Temporal workflow ID)
- `_global.params.workflowId` — workflow name, e.g. `"create_elixir_ticket_workflow"` (required; used as workflowName and for enum keys)
- `_global.params.version` — e.g. `"SNAPSHOT"` (required when addChildWorkflow is true)

## Lookup keys (already in lookup.properties)

- `global.childWorkflow.create_elixir_ticket_workflow.isSmart=true`
- `global.childWorkflow.create_elixir_ticket_workflow.actionEligibility=ADD_ALTERNATE_NUMBER`

Script reads them without `global.` prefix: `childWorkflow.create_elixir_ticket_workflow.isSmart`, `childWorkflow.create_elixir_ticket_workflow.actionEligibility`.

## Example state JSON (snippet)

If your workflow definition is JSON, the first state can look like:

```json
"register_elixir_child_workflow": {
  "instanceName": "register_elixir_child_workflow",
  "resourceId": "update_incident_v3",
  "resourceVersion": "1",
  "nextNode": "<your_previous_first_node_id>",
  "parameters": {
    "incidentId": "$.params.incidentId",
    "addChildWorkflow": true
  }
}
```

Set `startNode` (or equivalent) to `register_elixir_child_workflow` so this state runs first.
