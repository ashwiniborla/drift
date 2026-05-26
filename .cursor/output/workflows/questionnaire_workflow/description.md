# questionnaire_workflow

## Overview
- **ID**: `questionnaire_workflow`
- **Version**: `1`
- **Start node**: `process_fake_workflow_details`
- **Default failure node**: `default_failure`
- **Comment**: Handles questionnaire for fake; takes actions accordingly. After user responds, validates the selection, creates a questionnaire incident, updates it as solved with questionnaireData, and optionally triggers FakeWorkflow.

## Versioning
- Newly created workflow/node references use **SNAPSHOT**; publish when ready for LATEST/ACTIVE.

## States

| State | Resource ID | Next Node | Terminal | Parameters |
|-------|-------------|-----------|----------|------------|
| process_fake_workflow_details | process_fake_workflow_details | questionnaire_questions_check | no | issueId: `$.issueDetail.issueId`, undeliveredType: `$.params.undeliveredType` |
| questionnaire_questions_check | questionnaire_questions_check | (branch: prepare_questionnaire_instructions / default_failure) | no | questions: `$.process_fake_workflow_details.questions` |
| prepare_questionnaire_instructions | prepare_questionnaire_instructions | questionnaire_instructions | no | questions: `$.process_fake_workflow_details.questions` |
| questionnaire_instructions | questionnaire_instructions | validate_questionnaire_response | no | inputOptions: `$.prepare_questionnaire_instructions.inputOptions` |
| validate_questionnaire_response | validate_questionnaire_response | create_questionnaire_incident | no | questions: `$.process_fake_workflow_details.questions`, flowDirection: `$.process_fake_workflow_details.flowDirection` |
| create_questionnaire_incident | create_questionnaire_incident | update_questionnaire_incident | no | (none — reads from _global) |
| update_questionnaire_incident | update_questionnaire_incident | check_fake_workflow_required | no | incidentId: `$.create_questionnaire_incident.incidentId`, questionnaireData: `$.validate_questionnaire_response.questionnaireData` |
| check_fake_workflow_required | check_fake_workflow_required | (branch: questionnaire_success / questionnaire_success) | no | (none — reads from _global) |
| questionnaire_success | questionnaire_success | — | yes | — |
| default_failure | default_failure | — | yes | — |

## Flow
- **Start** → `process_fake_workflow_details` → `questionnaire_questions_check` (branch on questions):
  - If questions present and non-empty → `prepare_questionnaire_instructions` → `questionnaire_instructions` (INSTRUCTION — user responds)
    → `validate_questionnaire_response` → `create_questionnaire_incident` (HTTP POST) → `update_questionnaire_incident` (HTTP POST — mark solved)
    → `check_fake_workflow_required` (branch):
      - If `isFakeWorkflowRequired` == true → `questionnaire_success` **(placeholder — update to FakeWorkflow when designed)**
      - Else → `questionnaire_success` → **end**
  - Else → `default_failure` → **end**
- On failure → `default_failure` → **end**

## Notes
- **resourceVersion**: SNAPSHOT by default for new creations; switch to LATEST/ACTIVE after publish if your system expects it.
- **TODO**: When FakeWorkflow is designed, update `check_fake_workflow_required`'s true-branch `nextNode` to point to the FakeWorkflow node instead of `questionnaire_success`.
- HTTP nodes use `localhost:5500` as base URL and `imsv2_varadhi_client1` as targetClientId.
- The `questionnaire_instructions` node is an INSTRUCTION (WAITING state). See `resume_request.md` for the 3 resume scenarios.
