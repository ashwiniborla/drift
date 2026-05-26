# Process Fake Workflow Details

## Overview
- **ID**: `process_fake_workflow_details`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Reads `issueId` and `undeliveredType` from node parameters, sets flow direction (forward/reverse) based on `issueId`, and exposes both in the output for downstream BRANCH/routing.

## Parameters

| Parameter       | Description                          | Source                    |
|----------------|--------------------------------------|---------------------------|
| `issueId`      | Issue identifier (1111 → forward, 1112 → reverse) | Workflow state / node parameters |
| `undeliveredType` | Type of undelivered classification        | Workflow state / node parameters |

Parameters are accessed in the script as `_global.nodeParameters.issueId` and `_global.nodeParameters.undeliveredType`.

## Script Details

### transformer (dynamic)
- **File**: `worker/src/main/resources/scripts/fake_questionnaire/process_fake_workflow_details.groovy`
- **Context access**: `_global.nodeParameters.issueId`, `_global.nodeParameters.undeliveredType`
- **Logic summary**: If `issueId == 1111` set `flowDirection` to `FORWARD`; if `issueId == 1112` set to `REVERSE`. Pass through `undeliveredType`. Return map with `flowDirection`, `undeliveredType`, and `issueId`.

## Output

Returned map (stored in `_global.process_fake_workflow_details`):

| Key              | Type   | Description |
|------------------|--------|-------------|
| `flowDirection`  | String | `"FORWARD"` when issueId is 1111, `"REVERSE"` when issueId is 1112, else null |
| `undeliveredType`| (any)  | Value from node parameters |
| `issueId`        | (any)  | Value from node parameters (for downstream use) |

## Dependencies

- **Reads from _global**: `_global.nodeParameters.issueId`, `_global.nodeParameters.undeliveredType`
- **Expected previous nodes**: None (only node parameters required). Workflow state must bind `issueId` and `undeliveredType` to this node (e.g. `parameters: { "issueId": "$.someSource.issueId", "undeliveredType": "$.someSource.undeliveredType" }`).

## Notes

- A downstream BRANCH node can route using `_global.process_fake_workflow_details.flowDirection == 'FORWARD'` or `== 'REVERSE'`.
- If `issueId` is neither 1111 nor 1112, `flowDirection` is left null; caller can treat as error or default path.
