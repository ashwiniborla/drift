package scripts.fake_questionnaire
/**
 * process_fake_workflow_details.groovy
 * Sets flow direction from issueId and passes through undeliveredType for fake questionnaire workflow.
 * Questionnaire config (title, subTitle, questions) is loaded from enum store
 * key: questionnaire_config.issue.<issue_id>.questions_config.<undelivered_type>
 *
 * Returns: Map with undeliveredType, issueId, questions (config block from JSON or null), questionnaireType
 */
def global = _global;

def issueId = global?.nodeParameters?.issueId
def issueConfig = _enum_store?.questionnaire_config?.issue
def undeliveredType = global?.nodeParameters?.undeliveredType != null ? global.nodeParameters.undeliveredType.toString().trim() : null
if (undeliveredType == null || undeliveredType.isEmpty()) {
    throw new IllegalArgumentException("undeliveredType is required for the fake questionnaire workflow. Please provide a non-empty value (e.g. rfr, cnr).")
}

if (issueId == null || issueId?.isEmpty()) {
    throw new IllegalArgumentException("Issue Id is required to start")
}

def config = issueConfig.get(issueId?.toString())

def questions = config?.get("questions_config")?.get(undeliveredType.toLowerCase())

if (!questions) {
    throw new IllegalArgumentException("Questions not found for issue id:" + issueId + " underlivered type:" + undeliveredType.toLowerCase())
}

return [
        undeliveredType  : undeliveredType,
        questions        : questions,
        issueConfig      : config,
        questionnaireType: questions?.get("type")
]
