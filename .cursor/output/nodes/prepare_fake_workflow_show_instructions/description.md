# Prepare fake workflow show instructions

## Overview
- **ID**: `prepare_fake_workflow_show_instructions`
- **Type**: `GROOVY`
- **Version**: `1`
- **Purpose**: Build the `inputOptions` list for `fake_workflow_show_instructions` from reporting Oxford data (`fetch_order_oxford`).

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `dataVariable` | Oxford resolved-variable key (e.g. `v2OrderData_imsv2_varadhi_client1_reporting`) | Workflow state `parameters.dataVariable` |

## Script Details

### transformer (dynamic)
- **Location**: `worker/src/main/resources/scripts/fake_workflow/prepare_fake_workflow_show_instructions.groovy`
- **Context access**: `_global.fetch_order_oxford`, `_global.orderDetails`, `_global.nodeParameters.dataVariable`
- **Logic summary**: Reads `resolvedVariablesResponse.ORDER.pivotIdContextMap[orderId].resolvedVariables[dataVariable].value`, walks `oms3_aggregated_order.oms3_order_data.units` and `zulu_data.entityViews` for images/titles, formats promise dates (Asia/Kolkata), and returns two options: static text + `order_items` template + instructions body, then the Done button widget.

## Output
- **Returns**: `List` of option maps consumed by INSTRUCTION `possibleDynamicValues` (`fake_workflow_show_instructions`).

## Dependencies
- **Reads from _global**: `fetch_order_oxford`, `orderDetails`, `nodeParameters`
- **Expected previous nodes**: `e2e_fetch_order_details` (as `fake_workflow_fetch_oxford` with `contextOverrideKey: fetch_order_oxford`)

## Notes
- Use `useCase: reporting` and matching `dataVariable` on the Oxford fetch state so the reporting payload is present.
