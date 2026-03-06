/**
 * validate_questionnaire_response.groovy
 *
 * Validates the user's selected questionnaire option against the known question keys,
 * stores the selected option config, and determines questionnaireType + isFakeWorkflowRequired.
 *
 * Context:
 *   _global['questionnaire_instructions:viewResponse']?.selectedOptions  – resume response from INSTRUCTION
 *   _global.nodeParameters.questions    – questions config block from process_fake_workflow_details
 *   _global.nodeParameters.flowDirection – FORWARD or REVERSE
 *
 * Returns: Map { isValid, selectedOptionKey, selectedQuestionConfig, isFakeWorkflowRequired,
 *                questionnaireType, questionnaireData, buttonAction }
 */

def selectedOptions = _global['questionnaire_instructions:viewResponse']?.selectedOptions
def questions = _global?.nodeParameters?.questions
def flowDirection = _global?.nodeParameters?.flowDirection

def selectedOptionKey = selectedOptions?.fake_preference
def raiseClicked = selectedOptions?.raise == 'submit'
def closeClicked = selectedOptions?.close == 'close'

def buttonAction = raiseClicked ? 'RAISE' : (closeClicked ? 'CLOSE' : 'UNKNOWN')

def questionList = (questions instanceof Map && questions.containsKey('questions'))
        ? questions.questions
        : []

if (!(questionList instanceof List)) questionList = []

def matchedConfig = questionList.find { it?.key == selectedOptionKey }
def isValid = (matchedConfig != null && selectedOptionKey != null)

def isFakeWorkflowRequired = isValid ? (matchedConfig?.isFakeWorkflowRequired ?: false) : false
def nextWorkflow = isValid ? matchedConfig?.nextWorkflow : null

def questionnaireType = (flowDirection == 'FORWARD') ? 'FAKE_FORWARD' : 'FAKE_REVERSE'

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
        nextWorkflow           : nextWorkflow,
        questionnaireType      : questionnaireType,
        questionnaireData      : questionnaireData,
        buttonAction           : buttonAction
]
