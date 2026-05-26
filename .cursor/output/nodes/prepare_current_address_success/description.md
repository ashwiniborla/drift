# prepare_current_address_success

## Overview
- **ID**: `prepare_current_address_success`
- **Type**: `SUCCESS`
- **Version**: `1`
- **Purpose**: Terminal success node for `prepare_current_address` sub-workflow. It is excluded from the flattened parent graph because `sub_prepare_current_address` is configured with `includeLastNode: false`.

## Notes
- Never executes in a parent workflow directly; the flattener removes it and wires `get_current_address.nextNode` to the parent's continuation node.
