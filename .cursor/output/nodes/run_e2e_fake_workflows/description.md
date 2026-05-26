# Run E2E Fake Workflows

## Overview
- **ID**: `run_e2e_fake_workflows`
- **Type**: `CHILD`
- **Version**: `1`
- **Purpose**: Asynchronously launches the `e2e_fake_workflows` child workflow for end-to-end fake processing.

## Parameters

None.

## Configuration
- **Execution Mode**: `ASYNC` — parent workflow continues immediately without waiting.
- **Child Workflow ID**: `e2e_fake_workflows`
- **Child Workflow Version**: `LATEST`

## Dependencies
- **Expected previous nodes**: `create_elixir_ticket_child`

## Notes
- Runs after the Elixir ticket child is launched. Both child workflows execute in parallel since both are ASYNC.
