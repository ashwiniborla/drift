/**
 * prepare_questionnaire_instructions.groovy
 * Builds the full inputOptions list for the QuestionnaireInstructions screen from the questions config.
 *
 * Context:
 *   _global.nodeParameters.questions - config block {
 *     title: { iris: { irisKey, default } }, subTitle: { iris: { irisKey, default } },
 *     questions: [ { key, displayName: { iris: { irisKey, default } } }, ... ] }
 *   Legacy enum store may still use iris.key; script accepts irisKey ?: key for migration.
 *
 * Returns: Map with key "inputOptions" = List of Option-like maps for elixir_questionnaire_screen (question_header, question_title, question_subtitle, elixir_questionnaire_preference, raise_to_delivery_team_button, close_button_widget)
 */
def questions = _global?.nodeParameters?.questions
def inputOptions = []

if (questions == null || !(questions instanceof Map)) {
    return [inputOptions: inputOptions]
}

def titleIris = questions?.title?.iris
def subTitleIris = questions?.subTitle?.iris
def questionList = questions?.questions
if (!(questionList instanceof List)) questionList = []

def resolveIrisKey = { iris, fallback ->
    (iris?.irisKey != null ? iris.irisKey : (iris?.key != null ? iris.key : fallback))
}

// question_header – static "Request assistance"
inputOptions << [
        id          : 'question_header',
        description : 'Header',
        tags        : [values: ['ss.static_text']],
        instructions: [
                [
                        templateId       : 'iris_static_message',
                        templateVariables: [
                                irisKey: 'request_assistance_title',
                                defaultText: 'Request assistance'
                        ]
                ]
        ]
]

// question_title – from questions.title.iris
inputOptions << [
        id          : 'question_title',
        description : 'Title',
        tags        : [values: ['ss.static_text']],
        instructions: [
                [
                        templateId       : 'iris_static_message',
                        templateVariables: [
                                irisKey    : resolveIrisKey(titleIris, 'elixir.question.title'),
                                defaultText: (titleIris?.default != null ? titleIris.default : 'Help us schedule your next delivery attempt')
                        ]
                ]
        ]
]

// question_subtitle – from questions.subTitle.iris
inputOptions << [
        id          : 'question_subtitle',
        description : 'Subtitle',
        tags        : [values: ['ss.static_text']],
        instructions: [
                [
                        templateId       : 'iris_static_message',
                        templateVariables: [
                                irisKey    : resolveIrisKey(subTitleIris, 'elixir.question.subtitle'),
                                defaultText: (subTitleIris?.default != null ? subTitleIris.default : 'To help us plan, could you let us know your preference for the recent reschedule request')
                        ]
                ]
        ]
]

// elixir_questionnaire_preference – single select, possibleValues from questions.questions
def possibleValues = questionList.collect { item ->
    def iris = item?.displayName?.iris
    if (iris != null) {
        def optionFallback = item?.key != null ? "elixir.question.answer.${item.key}" : 'elixir.question.answer'
        [
                displayValue: (iris?.default != null ? iris.default : (item?.key ?: '')),
                metaData    : [
                        irisKey: resolveIrisKey(iris, optionFallback)
                ],
                value       : (item?.key != null ? item.key : '')
        ]
    } else {
        [
                displayValue: item.displayName.value,
                metaData    : null,
                value       : (item?.key != null ? item.key : '')
        ]
    }
}
inputOptions << [
        id            : 'elixir_questionnaire_preference',
        description   : 'Single select preference',
        tags          : [values: ['ss.single_select', 'ss.next_request_data']],
        possibleValues: possibleValues
]

// raise_to_delivery_team_button
inputOptions << [
        id            : 'raise',
        description   : 'Raise to delivery team',
        tags          : [values: ['ss.button_widget', 'ss.next_request_data']],
        possibleValues: [
                [
                        displayValue: 'Raise to delivery team',
                        metaData    : [
                                irisKey: 'elixir.button.raise_request'
                        ],
                        value       : 'submit'
                ]
        ]
]

return [inputOptions: inputOptions]
