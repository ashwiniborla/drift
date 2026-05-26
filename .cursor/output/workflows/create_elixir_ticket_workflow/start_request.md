# create_elixir_ticket_workflow — Start Request

## Overview

Documents the request to start this workflow. The **first state** is `create_elixir_ticket_branch_skip_incident_entry`, which routes to `register_elixir_child_workflow` (update_incident with child add) unless `ignore_incident_update` is boolean `false` (caller handles registration and post-create incident update outside the workflow).

## Required params (when `register_elixir_child_workflow` runs — i.e. `ignore_incident_update` is not boolean `false`)

| Param | Description |
|-------|-------------|
| `workflowId` | Temporal workflow ID; must be set in _global.workflowId when the workflow runs (typically injected by the runtime). |
| `workflowId` (params) | Workflow name used for v3ChildWorkflowRequest and for enum keys; e.g. `"create_elixir_ticket_workflow"`. Script reads _global.params.workflowId — **required**. |
| `version` | Workflow version; e.g. `"SNAPSHOT"`. Script reads _global.params.version — **required** when addChildWorkflow is true. |
| incidentId | Incident to update; passed as node parameter to the update_incident node (e.g. from issueDetail or params). |

## Optional

| Param | Description |
|-------|-------------|
| `ignore_incident_update` | Boolean. **`false`** — skip `register_elixir_child_workflow` and `update_incident_elixir_requested` (caller updates incident/child externally). **Omitted, `null`, or any value other than boolean `false`** — normal flow; both nodes run. |

Other workflow start params as needed by later nodes (e.g. issueId, orderId).

## Sample (minimal for first node)

Ensure when the workflow runs, _global has:
- `_global.workflowId` (Temporal workflow ID)
- `_global.params.workflowId` = `"create_elixir_ticket_workflow"`
- `_global.params.version` = `"SNAPSHOT"` (or desired version)

When the branch sends the run to registration, workflow node `register_elixir_child_workflow` (update_incident) receives node parameters:
- `incidentId`: from context (e.g. `$.params.incidentId` or `$.issueDetail.issueId`)
- `addChildWorkflow`: `true` (static)
