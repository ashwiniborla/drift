# Fake Workflow

## Overview
- **ID**: `fake_workflow`
- **Version**: `1`
- **Start node**: `fake_workflow_fetch_oxford`
- **Default failure node**: `default_failure`
- **Comment**: This will handle cases for both forward and fake. This can be used as both SubWorkflow and Making workflow.

## States

| State | Resource ID | Next Node | Terminal | Parameters |
|-------|-------------|-----------|----------|------------|
| fake_workflow_fetch_oxford | e2e_fetch_order_details | fake_check_similarity_and_create_incident | no | dataVariable (`v2OrderData_imsv2_varadhi_client1_reporting`), useCase (`reporting`); context → `fetch_order_oxford` |
| fake_check_similarity_and_create_incident | check_similarity_and_create_incident | fake_incident_created_check | no | workflow_id, issue_detail, queue_detail, customer, order_details, override_issue_id |
| fake_incident_created_check | fake_incident_created_check | (branch) | no | — |
| create_elixir_ticket_child | create_elixir_ticket_child | run_e2e_fake_workflows | no | — |
| run_e2e_fake_workflows | run_e2e_fake_workflows | fake_client_check | no | — |
| fake_client_check | fake_client_check | (branch) | no | — |
| prepare_fake_workflow_show_instructions | prepare_fake_workflow_show_instructions | fake_workflow_show_instructions | no | dataVariable |
| fake_workflow_show_instructions | fake_workflow_show_instructions | add_notes_instruction | no | — |
| add_notes_instruction | add_notes_instruction | update_incident | no | — |
| update_incident | update_incident | fake_workflow_success | no | incidentId, notesText |
| fake_workflow_success | fake_workflow_success | — | yes | — |
| default_failure | default_failure | — | yes | — |

## Flow

1. **fake_workflow_fetch_oxford** — POST Oxford resolved variables with `useCase: reporting` and `dataVariable: v2OrderData_imsv2_varadhi_client1_reporting`; response merged into context as `fetch_order_oxford`.
2. **fake_check_similarity_and_create_incident** — Creates an incident via checkSimilarityAndCreate API. When used as a subworkflow, `override_issue_id` from `process_fake_workflow_details.issueConfig.fakeIssueId` replaces the parent's issueDetail; when run directly, `override_issue_id` is absent and the original issueDetail is used.
3. **fake_incident_created_check** — BRANCH: if `duplicateIncidentFound == false` (new incident) -> child workflows path; otherwise -> SA client check.
4. **create_elixir_ticket_child** — ASYNC CHILD: launches `create_elixir_ticket_workflow`.
5. **run_e2e_fake_workflows** — ASYNC CHILD: launches `e2e_fake_workflows`.
6. **fake_client_check** — BRANCH: if `threadContext.clientId == "sa"` -> `prepare_fake_workflow_show_instructions`; otherwise -> success.
7. **prepare_fake_workflow_show_instructions** — GROOVY: builds `inputOptions` (static text, `order_items` from Oxford units + zulu images, long instructions copy, Done button) for the next INSTRUCTION node.
8. **fake_workflow_show_instructions** — INSTRUCTION: `layoutId` `elixir_fake_attempt`; widgets from `possibleDynamicValues` → prep node output.
9. **add_notes_instruction** — INSTRUCTION: free-text `agent_notes` and Update Incident button (`add_notes_instruction` layout).
10. **update_incident** — HTTP: `notesText` from `$.add_notes_instruction.agent_notes`, `incidentId` from create-incident step.
11. **fake_workflow_success** — SUCCESS: workflow ends.

## Notes
- Both child workflows (Elixir ticket + e2e fake) are ASYNC — they run in parallel and do not block the parent.
- The SA instruction screens are only shown when `clientId == "sa"`; all other clients skip directly to success.
- Example WAITING payloads and notes binding are documented under `workflowWaitingContractSamples` in [oxfordResponse.json](oxfordResponse.json) (alongside the reporting Oxford sample).
