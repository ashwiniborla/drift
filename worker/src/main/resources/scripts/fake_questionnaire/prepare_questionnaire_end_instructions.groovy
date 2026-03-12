/**
 * prepare_questionnaire_end_instructions.groovy
 * Builds the inputOptions list for the questionnaire end screen
 * (request raised to delivery team confirmation + redirect Submit button).
 *
 * Context:
 *   No parameters required — static end-screen content.
 *
 * Returns: Map with key "inputOptions" = List of Option-like maps for
 *          the request_raised_to_delivery_team_end screen.
 */

def inputOptions = []

// Static success message
inputOptions << [
        id          : 'request_raised_to_delivery_team_message',
        description : 'Request raised success message',
        tags        : [values: ['ss.static_text']],
        instructions: [
                [
                        templateId       : 'iris_static_message',
                        templateVariables: [
                                enum       : 'request_raised_to_delivery_team',
                                defaultText: 'Request raised to the delivery team'
                        ]
                ]
        ]
]

// Submit button with redirect: true in metaData — tells UI to redirect, not call resume
inputOptions << [
        id            : 'submit_button',
        description   : 'Submit button — redirect: true tells UI to redirect instead of calling resume',
        tags          : [values: ['ss.button_widget']],
        possibleValues: [
                [
                        displayValue: 'Submit',
                        value       : 'SUBMIT',
                        metaData    : [redirect: true]
                ]
        ]
]

return [inputOptions: inputOptions]
