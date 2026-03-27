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



def layoutId = "phone_number_updated_successfully"
def submitInputTags = ['ss.button_widget', "ss.next_request_data"]
def odRedirection = false

def inputOptions = []

inputOptions << [
        id            : 'phone_number_updated_successfully_message',
        description   : 'Phone number updated successfully message',
        tags          : [values: ['ss.static_text']],
        "instructions": [
                [
                        "templateId"       : "iris_static_message",
                        "templateVariables": [
                                "irisKey"      : "elixir.phone_confirmation.success_message",
                                "defaultText"  : "Your phone number has been updated successfully"
                        ]
                ]
        ]

]


inputOptions << [
        id            : 'phone_number_updated_successfully_okay_button',
        description   : 'Okay',
        tags          : [values: submitInputTags],
        possibleValues: [
                [
                        displayValue: 'Okay',
                        value       : 'SUBMIT',
                        metaData    : [
                                odRedirection: odRedirection,
                                irisKey      : "elixir.button.okay"
                        ]
                ]
        ]
]

return [inputOptions: inputOptions, layoutId: layoutId]
