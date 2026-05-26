# Create Elixir Ticket Child

## Overview
- **ID**: `create_elixir_ticket_child`
- **Type**: `CHILD`
- **Version**: `1`
- **Purpose**: Asynchronously launches the `create_elixir_ticket_workflow` child workflow to create an Elixir ticket for the newly created incident.

## Parameters

None.

## Configuration
- **Execution Mode**: `ASYNC` — parent workflow continues immediately without waiting.
- **Child Workflow ID**: `create_elixir_ticket_workflow`
- **Child Workflow Version**: `LATEST`

## Dependencies
- **Expected previous nodes**: `fake_incident_created_check` (only reached when a new incident was created)

## Notes
- The child workflow runs independently; its success or failure does not block the parent fake_workflow.
