/**
 * validate_questionnaire_response.groovy
 *
 * Validates the user's selected questionnaire option against the known question keys,
 * stores the selected option config, and determines questionnaireType + isFakeWorkflowRequired.
 *
 * Context:
 *   _global['questionnaire_instructions:viewResponse']?.selectedOptions  – resume response from INSTRUCTION
 *   _global.nodeParameters.questions    – questions config block from process_fake_workflow_details
 *
 * Returns: Map { isValid, selectedOptionKey, selectedQuestionConfig, isFakeWorkflowRequired,
 *                questionnaireType, questionnaireData, buttonAction }
 */

def selectedOptions = _global['questionnaire_instructions:viewResponse']?.selectedOptions
def questions = _global?.nodeParameters?.questions
def questionnaireType = questions?.type

def selectedOptionKey = selectedOptions?.fake_preference

def questionList = questions?.questions ?: []

if(questionList.isEmpty()) {
    throw new IllegalArgumentException("Questions not found")
}

def matchedConfig = questionList.find { it?.key == selectedOptionKey }
def isValid = (matchedConfig != null && selectedOptionKey != null)

if(!isValid) {
    throw new IllegalArgumentException("In valid Option selected")
}

def isFakeWorkflowRequired = isValid ? (matchedConfig?.isFakeWorkflowRequired ?: false) : false

def questionnaireData = [
        questionnaireType: questionnaireType,
        response         : selectedOptionKey ?: '',
        createdAt        : new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
]

return [
        isValid                : isValid,
        selectedOptionKey      : selectedOptionKey,
        selectedQuestionConfig : matchedConfig,
        isFakeWorkflowRequired : isFakeWorkflowRequired,
        questionnaireData      : questionnaireData
]
