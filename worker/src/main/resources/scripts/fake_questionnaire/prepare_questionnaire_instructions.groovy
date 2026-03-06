/**
 * prepare_questionnaire_instructions.groovy
 * Builds the full inputOptions list for the QuestionnaireInstructions screen from the questions config.
 *
 * Context:
 *   _global.nodeParameters.questions - config block { title: { iris: { key, default } }, subTitle: { iris: { key, default } }, questions: [ { key, displayName: { iris: { key, default } }, ... } ] }
 *
 * Returns: Map with key "inputOptions" = List of Option-like maps for elixir_questionnaire_screen (question_header, question_title, question_subtitle, fake_preference, raise_to_delivery_team_button, close_button_widget)
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

// question_header – static "Request assistance"
inputOptions << [
        id          : 'question_header',
        description : 'Header',
        tags        : [values: ['ss.static_text']],
        instructions: [
                [
                        templateId       : 'iris_static_message',
                        templateVariables: [enum: 'request_assistance_title', defaultText: 'Request assistance']
                ]
        ]
]

// question_title – from questions.title.iris (enum = key, defaultText = default)
inputOptions << [
        id          : 'question_title',
        description : 'Title',
        tags        : [values: ['ss.static_text']],
        instructions: [
                [
                        templateId       : 'iris_static_message',
                        templateVariables: [
                                key        : (titleIris?.key != null ? titleIris.key : 'help_schedule_next_delivery_attempt_title'),
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
                                key        : (subTitleIris?.key != null ? subTitleIris.key : 'reschedule_preference_helper_text'),
                                defaultText: (subTitleIris?.default != null ? subTitleIris.default : 'To help us plan, could you let us know your preference for the recent reschedule request')
                        ]
                ]
        ]
]

// fake_preference – single select, possibleValues from questions.questions
def possibleValues = questionList.collect { item ->
    def iris = item?.displayName?.iris
    if (iris != null) {
        [
                displayValue: (iris?.default != null ? iris.default : (item?.key ?: '')),
                metaData    : [
                        templateId       : 'iris_static_message',
                        templateVariables: [
                                key        : iris.key,
                                defaultText: (subTitleIris?.default != null ? subTitleIris.default : 'To help us plan, could you let us know your preference for the recent reschedule request')
                        ]
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
        id            : 'fake_preference',
        description   : 'Single select preference',
        tags          : [values: ['ss.single_select']],
        possibleValues: possibleValues
]

// raise_to_delivery_team_button
inputOptions << [
        id            : 'raise',
        description   : 'Raise to delivery team',
        tags          : [values: ['ss.button_widget']],
        possibleValues: [
                [displayValue: 'Raise to delivery team', metaData: null, value: 'submit']
        ]
]

// close_button_widget
inputOptions << [
        id            : 'close',
        description   : 'Close',
        tags          : [values: ['ss.button_widget']],
        possibleValues: [
                [displayValue: 'Close', metaData: null, value: 'close']
        ]
]

return [inputOptions: inputOptions]
