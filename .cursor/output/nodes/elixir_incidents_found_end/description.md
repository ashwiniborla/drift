# Elixir Incidents Found End

## Overview
- **ID**: `elixir_incidents_found_end`
- **Type**: SUCCESS
- **Version**: `1`
- **Purpose**: Terminal node when incidents filter returned count > 0 (incidents already exist for the criteria); workflow ends successfully without creating a new ticket.

## Parameters
None.

## Output
Terminal success; no output to _global.

## Dependencies
- **Expected previous node**: Reached via `elixir_check_filter_count` when count > 0.

## Notes
- executionMode: SYNC (required by SuccessNode).
