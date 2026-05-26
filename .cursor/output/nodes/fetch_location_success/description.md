# Fetch Location Success

## Overview
- **ID**: `fetch_location_success`
- **Type**: `SUCCESS`
- **Version**: `1`
- **Purpose**: Terminal SUCCESS node for the `fetch_location` workflow. Reached when either (a) nudge was not required and the evaluate_location check passed, or (b) the location instruction screen was shown and the workflow completed.

## Notes
- When `fetch_location` is used as a subworkflow via `sub_fetch_location` with `includeLastNode: false`, this node is excluded from flattening and the parent workflow's `nextNode` takes over.
