/**
 * prepare_fap_success_instruction.groovy
 *
 * Builds inputOptions for FAP success (message + Submit). odRedirection follows post-questionnaire
 * location requirement: if location collection still needed, odRedirection is false so parent continues;
 * otherwise true so workflow ends with redirect.
 *
 * Context:
 *   _global.validate_questionnaire_response?.selectedQuestionConfig?.postScreensRequired?.location
 *   When absent (e.g. standalone smart workflow), odRedirection defaults to true.
 *
 * Returns: [ inputOptions: List ]
 */

def needLocation = _global.validate_questionnaire_response?.selectedQuestionConfig?.postScreensRequired?.location == true
def odRedirection = !needLocation

def inputOptions = []

inputOptions << [
        id          : 'fap_success_message',
        description : 'Address/phone update success message',
        tags        : [values: ['ss.static_text']],
        instructions: [
                [
                        templateId       : 'iris_static_message',
                        templateVariables: [
                                irisKey    : 'elixir.request_raised_to_delivery_team_message',
                                defaultText: "We've updated your request successfully"
                        ]
                ]
        ]
]


def submitInputTags = ['ss.button_widget']

if(needLocation) {
    submitInputTags.add("ss.button_widget")
}

inputOptions << [
        id            : 'submit_button',
        description   : 'Submit — odRedirection drives redirect vs resume to parent',
        tags          : [values: submitInputTags],
        possibleValues: [
                [
                        displayValue: 'Submit',
                        value       : 'SUBMIT',
                        metaData    : [odRedirection: odRedirection]
                ]
        ]
]

return [inputOptions: inputOptions]
