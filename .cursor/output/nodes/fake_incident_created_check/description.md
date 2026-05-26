# Fake Incident Created Check

## Overview
- **ID**: `fake_incident_created_check`
- **Type**: `BRANCH`
- **Version**: `1`
- **Purpose**: After the create-incident HTTP call, routes the workflow based on whether a new incident was created or a duplicate was found.

## Parameters

None.

## Branch Rules

| # | Condition | Next Node |
|---|-----------|-----------|
| 1 | `duplicateIncidentFound == false` (new incident) | `create_elixir_ticket_child` |
| default | duplicate found / fallback | `fake_client_check` |

## Script Details

### Rule 1 (in-node)
- **Logic**: Checks `_global.fake_check_similarity_and_create_incident?.duplicateIncidentFound == false`. Returns `true` when the incident is freshly created (not a duplicate).

## Dependencies
- **Reads from _global**: `fake_check_similarity_and_create_incident.duplicateIncidentFound`
- **Expected previous nodes**: `fake_check_similarity_and_create_incident`

## Notes
- When `duplicateIncidentFound` is `true` (or null), the default path skips child workflows and goes directly to the SA client check.
