# prepare_sa_fake_workflow_complete

## Overview
- **ID**: `prepare_sa_fake_workflow_complete`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Builds `inputOptions` for the SA-only completion screen (`sa_fake_workflow_complete`) so `workflow_success_instructions` receives the real `incidentId`.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| (none) | — | — |

## Script Details

### transformer (dynamic)
- **Location**: `src/main/resources/scripts/fake_workflow/prepare_sa_fake_workflow_complete.groovy`
- **Context access**: `_global.fake_check_similarity_and_create_incident`
- **Logic summary**: Reads `incident_id` from the create-incident step and returns a single `inputOptions` entry matching the `sa.complete_workflow_component` + `workflow_success_instructions` shape.

## Output
- **Returns**: `Map` with key `inputOptions` (list of option maps) consumed by the INSTRUCTION node via workflow `parameters`.

## Dependencies
- **Reads from _global**: `fake_check_similarity_and_create_incident.incident_id`
- **Expected previous nodes**: `fake_check_similarity_and_create_incident` must have run; typically invoked immediately after `update_incident`.

## Notes
- Only the SA client path reaches this prepare step in `fake_workflow` (non-SA routes to `fake_workflow_success` earlier at `fake_client_check`).
