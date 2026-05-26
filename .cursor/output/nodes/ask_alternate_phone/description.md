# ask_alternate_phone

## Overview
- **ID**: `ask_alternate_phone`
- **Type**: `INSTRUCTION`
- **Version**: `1`
- **Purpose**: Pauses the workflow and presents the customer-facing UI for confirming contact details and optionally adding/updating an alternate phone number. The `inputOptions` are built dynamically by the preceding `prepare_alternate_phone_response` GROOVY node and injected via workflow parameter binding.

## Parameters

| Parameter | Description | Source |
|-----------|-------------|--------|
| `inputOptions` | Fully built list of UI widgets for the screen | `$.prepare_alternate_phone_response.inputOptions` |

## Script Details

### layoutId (static)
- **Value**: `elixir_proactive_get_alternate_number_layout`
- **Type**: `STATIC`

### inputOptions (dynamic)
- **Source**: `nodeParameters.inputOptions` (resolved at runtime from the `prepare_alternate_phone_response` GROOVY node output)
- The node uses `possibleDynamicValues: "nodeParameters.inputOptions"` — no static widgets are embedded in the node definition itself

## Output
- **Resume payload stored in**: `_global.ask_alternate_phone`

## Dependencies
- **Expected previous node**: `prepare_alternate_phone_response`
- **Reads from `_global`**: none directly (all data pre-computed by GROOVY node)

## Notes
- Workflow is in `WAITING` state until resumed
- Resume scenarios are documented in `resume_request.md`
- The two UI contracts (no alt phone / alt phone present) are handled entirely in `prepare_alternate_phone_response.groovy`; this node definition is contract-agnostic
