# Resume Request — questionnaire_instructions

## Overview
- **Node ID**: `questionnaire_instructions`
- **Purpose**: Presents the fake-delivery questionnaire screen (title, subtitle, single-select preference options, and action buttons). The resume request carries the user's selected option and button action back to the workflow.

## Resume scenarios

### Scenario 1: User selects an option and clicks "Raise to delivery team"
- **Description**: The user picks one of the questionnaire options (e.g. "No one has contacted me") and taps the "Raise to delivery team" button to proceed.
- **Sample request / payload**:
```json
{
    "selectedOptions": {
        "elixir_questionnaire_preference": "no_one_has_contacted_me",
        "raise": "submit"
    }
}
```
- **Outcome**: Workflow continues → `validate_questionnaire_response` validates the option, then creates and updates the incident. If `isFakeWorkflowRequired` is true for the selected option, the FakeWorkflow is triggered.

### Scenario 2: User selects a different option and clicks "Raise to delivery team"
- **Description**: The user picks a different option (e.g. "Partner contacted, but did not deliver") and taps "Raise to delivery team".
- **Sample request / payload**:
```json
{
    "selectedOptions": {
        "elixir_questionnaire_preference": "prtnr_contacted_didnt_deliver",
        "raise": "submit"
    }
}
```
- **Outcome**: Workflow continues → validation succeeds, incident is created and updated. `isFakeWorkflowRequired` may be false for this option, so workflow ends at success without FakeWorkflow.

### Scenario 3: User clicks "Close" without raising
- **Description**: The user opts to close the questionnaire without raising to the delivery team. They may or may not have selected a preference.
- **Sample request / payload**:
```json
{
    "selectedOptions": {
        "elixir_questionnaire_preference": "prtnr_contacted_but_i_was_away",
        "close": "close"
    }
}
```
- **Outcome**: Workflow continues → `validate_questionnaire_response` detects `buttonAction = CLOSE`. Incident is still created and updated with the selected response, but the close action can be used for downstream logic if needed.

## Notes
- The `elixir_questionnaire_preference` key corresponds to the `value` field of the selected possibleValue in the single-select widget.
- Valid option keys are loaded from `questions_config.json` and depend on the flow direction (forward/reverse) and undelivered type (rfr/cnr).
- The `raise` and `close` keys correspond to the button widgets defined in `prepare_questionnaire_instructions.groovy`.
