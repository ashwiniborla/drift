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

inputOptions << [
        id          : "request_raised_header",
        description : "Header",
        tags        : [
                values: [
                        "ss.static_text"
                ]
        ],
        instructions: [
                [
                        templateId       : "iris_static_message",
                        templateVariables: [
                                irisKey    : "elixir.address.header",
                                defaultText: "Request raised"
                        ]
                ]
        ]
]

inputOptions << [
        id          : "request_raised_title",
        description : "We have raised request to delivery team",
        tags        : [
                values: [
                        "ss.static_text",
                        "ss.success_popup"
                ]
        ],
        instructions: [
                [
                        templateId       : "iris_static_message",
                        templateVariables: [
                                irisKey    : "elixir.request_raised_to_delivery_team_message",
                                defaultText: "We've raised your request to the delivery team"
                        ]
                ]
        ]
]

inputOptions << [
        id          : "request_raised_subtitle",
        description : "Sorry for inconvenience caused",
        tags        : [
                values: [
                        "ss.text_widget",
                        "ss.success_popup"
                ]
        ],
        instructions: [
                [
                        templateId       : "iris_static_message",
                        templateVariables: [
                                irisKey    : "elixir.sorry_for_inconvenience_caused",
                                defaultText: "Sorry for the inconvenience caused"
                        ]
                ]
        ]
]

inputOptions << [
        id            : "raise_to_delivery_team_button",
        description   : "Raise to delivery team",
        tags          : [
                values: [
                        "ss.button_widget",
                        "ss.success_popup"
                ]
        ],
        possibleValues: [
                [
                        value       : "done",
                        displayValue: "Done",
                        metaData    : [
                                irisKey: "elixir.button.done_button"
                        ]
                ]
        ]
]

return [inputOptions: inputOptions]
