# Phone number update failed

## Overview

- **ID**: `phone_number_update_failed`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Shows a static Iris-backed message and an **Okay** button while the workflow is in **WAITING**, using layout `phone_number_updated_failure`. The surrounding platform typically adds `incidentId`, `workflowId`, and wraps the node output under `view` in the API response.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| *(none)* | All layout, status, and widgets are static in the node definition | — |

## Script Details

- **None** — `layoutId`, `workflowStatus`, and `inputOptions` are all static (`STATIC` / literal option definitions).

## Output

- **Returns**: Instruction payload (layout, `workflowStatus`, `inputOptions`) as produced by the INSTRUCTION executor. Client-facing envelopes often mirror:

```json
{
  "incidentId": "IN<INCIDENT_ID>",
  "workflowId": "WF-IN<INCIDENT_ID>",
  "workflowStatus": "WAITING",
  "view": { "layoutId": "...", "inputOptions": [ ... ] }
}
```

where `incidentId` / `workflowId` come from workflow context, not from this node JSON.

## Dependencies

- **Reads from _global**: None required for this static node.
- **Expected previous nodes**: None specific; wire in a workflow when you need this screen after a failed phone update path.

## Notes

- Layout id is `phone_number_updated_failure` (as specified). The Iris key and `defaultText` currently describe a **success** message; align copy with product if this screen is strictly for failure.
- `templateId` `iris_static_message` uses `templateVariables.irisKey` (not legacy `key`).
