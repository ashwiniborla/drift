# FAP Address Update Success

## Overview
- **ID**: `fap_address_update_success`
- **Type**: `SUCCESS`
- **Version**: `1`
- **Purpose**: Terminal node for the `fap_address_update` sub-workflow. Marks successful completion of address creation (User Service) and chore confirmation. When inlined as a SUB_WORKFLOW, the flattener rewires this node to continue to the parent's `fap_success_instruction`.

## Notes
- **Only acts as a true terminal** when `fap_address_update` is executed standalone.
- When inlined, `rewireChainEnd` sets `nextNode = "fap_success_instruction"` and `end: false`.
