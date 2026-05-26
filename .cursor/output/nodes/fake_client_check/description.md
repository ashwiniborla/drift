# Fake Client Check

## Overview
- **ID**: `fake_client_check`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: Routes the workflow based on whether the client is SA (service agent). SA clients get instruction screens; other clients go directly to success.

## Parameters

None.

## Branch Rules

| # | Condition | Next Node |
|---|-----------|-----------|
| 1 | `threadContext.clientId == "sa"` | `fake_workflow_show_instructions` |
| default | not SA | `fake_workflow_success` |

## Script Details

### Rule 1 (in-node)
- **Logic**: Checks `_global.threadContext?.clientId == "sa"`. Returns `true` when the workflow is being run by a service agent.

## Dependencies
- **Reads from _global**: `threadContext.clientId`
- **Expected previous nodes**: `fake_incident_created_check` (false path) or `run_e2e_fake_workflows` (true path)

## Notes
- Both the "new incident" and "duplicate incident" paths converge at this node.
