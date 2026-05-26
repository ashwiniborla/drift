# Elixir Check Filter Count

## Overview
- **ID**: `elixir_check_filter_count`
- **Type**: BRANCH
- **Version**: `1`
- **Purpose**: Branches on `count` from previous node `elixir_filter_incidents`. If count > 0, flow goes to `elixir_waiting_for_response`; otherwise flow goes to `elixir_create_ticket`.

## Parameters
None. Reads `_global.elixir_filter_incidents.count` from previous node output.

## Script Details

### Rule (in-node)
- **Condition**: `_global.elixir_filter_incidents?.count > 0` (with null-safe default 0).
- **When true**: nextNode = `elixir_waiting_for_response` (wait for Elixir callback).
- **When false**: defaultNode = `elixir_create_ticket` (create new ticket).

## Output
No output; routes workflow to next state.

## Dependencies
- **Reads from _global**: `elixir_filter_incidents.count`
- **Expected previous node**: `elixir_filter_incidents`

## Notes
- When you add the node for the "count == 0" path, update this node's `defaultNode` to point to that new node instead of `default_failure`.
