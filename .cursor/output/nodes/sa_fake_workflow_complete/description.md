# sa_fake_workflow_complete

## Overview
- **ID**: `sa_fake_workflow_complete`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Terminal SA success UI for `fake_workflow` after `update_incident` — layout `sa_workflow_complete_screen`, disposition `SA_WORKFLOW_COMPLETE`, status `COMPLETED`, with `workflow_success_instructions` showing the incident id.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `inputOptions` | Option list built by `prepare_sa_fake_workflow_complete` | `$.prepare_sa_fake_workflow_complete.inputOptions` |

## Script Details

- **Layout / disposition / workflowStatus**: `STATIC` in node definition (no scripts).

## Output
- **Returns**: Instruction payload for the client (`view.layoutId`, `view.inputOptions`, plus framework fields such as `incidentId` / `workflowId` from workflow context where applicable).

## Dependencies
- **Reads from _global**: Indirectly via `nodeParameters.inputOptions` populated from prepare node output.
- **Expected previous nodes**: `prepare_sa_fake_workflow_complete` must run first; workflow state passes `inputOptions` into this node.

## Notes
- Used only on the SA path in `fake_workflow` (the branch that goes through `update_incident`). Non-SA clients still end on `fake_workflow_success` from `fake_client_check`.
