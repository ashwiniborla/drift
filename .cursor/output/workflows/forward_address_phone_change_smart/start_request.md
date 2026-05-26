# forward_address_phone_change_smart — Start Request

## Overview

Documents the request to start this workflow. The workflow **GETs the incident first** (`get_incident_for_elixir` → `get_incident` + `incident_extractor/extract_elxr_tkt_from_get_incident.groovy`) to obtain **`elixirTicketId`** from `incidentResponseData.customFields.elxrTkt`. It then registers as a V3 child on the incident (`update_incident`), runs FAP eligibility/feasibility, collects the new phone via smart UI, updates the address, then optionally notifies Elixir via Varadhi when `elixir.actionConfig[updateType]` has `responseConfig` (see `prepare_elixir_ticket_update`).

## Required params

| Param | Description |
|-------|-------------|
| `workflowId` | Workflow name for child registration; e.g. `"forward_address_phone_change_smart"`. |
| `version` | Workflow version; e.g. `"SNAPSHOT"`. |
| `incidentId` | Incident external id (GET incident, child registration, completion update). |

## Optional params

| Param | Description |
|-------|-------------|
| `issueId` | Issue context (see `context.json` / eligibility). |
| `includeV3SmartWorkflow` | Passed to IMS GET as `includeV3SmartWorkflow=true` when truthy; default is false (query param omitted). |
| `elixirTicketId` | **Deprecated for this workflow** — ticket id is read from the incident via `get_incident_for_elixir`. Kept only for backward compatibility with older callers; workflow binding uses `$.get_incident_for_elixir.elixirTicketId`. |

## Sample `params` fragment

```json
{
  "workflowId": "forward_address_phone_change_smart",
  "version": "SNAPSHOT",
  "issueId": "2111",
  "incidentId": "IN26031604042292015033"
}
```

## JsonPath bindings in workflow

- `get_incident_for_elixir` receives `incidentId`: `$.params.incidentId`, `includeV3SmartWorkflow`: `false` by default (override via workflow edit to `$.params.includeV3SmartWorkflow` if needed).
- `prepare_elixir_ticket_update` receives `elixirTicketId`: `$.get_incident_for_elixir.elixirTicketId` (from GET incident + extractor).
- `updateType` is currently fixed in workflow state as `"ALT_PH_NUMBER_REQUIRED"` (override via workflow edit or future param if needed).
