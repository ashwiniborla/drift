package scripts.fake_questionnaire
/**
 * process_fake_workflow_details.groovy
 * Sets flow direction from issueId and passes through undeliveredType for fake questionnaire workflow.
 * Questionnaire config (title, subTitle, questions) is loaded from questions_config.json
 * keyed by "fake_<flowDirection>_<undeliveredType>" (e.g. fake_forward_rfr, fake_reverse_cnr).
 *
 * Context:
 *   _global.nodeParameters.issueId         - issue ID (1111 → FORWARD, 1112 → REVERSE)
 *   _global.nodeParameters.undeliveredType - undelivered type (e.g. rfr, cnr)
 *
 * Config: scripts/fake_questionnaire/questions_config.json (same folder as this script, classpath)
 *
 * Returns: Map with flowDirection, undeliveredType, issueId, questions (config block from JSON or null)
 */
def global = _global;
def issueId = global?.nodeParameters?.issueId
def forwardIssueId = 1111
def reverseIssueId = 1112
def undeliveredType = global?.nodeParameters?.undeliveredType != null ? global.nodeParameters.undeliveredType.toString().trim() : null
if (undeliveredType == null || undeliveredType.isEmpty()) {
    throw new IllegalArgumentException("undeliveredType is required for the fake questionnaire workflow. Please provide a non-empty value (e.g. rfr, cnr).")
}

// Load questionnaire config from questions_config.json (classpath: scripts/fake_questionnaire/questions_config.json)
// Use this script's classloader (GroovyClassLoader / worker classpath), not context classloader, so the resource is found.
// Variable name must not be 'cl' - other code may do (cl + "==========") and Groovy would call plus() on the classloader.
def questionnaireConfig = [:]
def resourcePath = 'scripts/fake_questionnaire/questions_config.json'
try {
    def stream = null
    ClassLoader loaderForResource = this.getClass().getClassLoader()
    while (loaderForResource != null && stream == null) {
        stream = loaderForResource.getResourceAsStream(resourcePath)
        loaderForResource = loaderForResource.getParent()
    }
    if (stream == null) {
        stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(resourcePath)
    }
    if (stream != null) {
        try {
            questionnaireConfig = new groovy.json.JsonSlurper().parse(stream) as Map
        } finally {
            stream.close()
        }
    }
} catch (Exception e) {
    // leave questionnaireConfig empty if file missing or parse error
}

def flowDirection = null
def questionsKey = null;
if (issueId != null) {
    def id = null
    if (issueId instanceof Number) {
        id = issueId
    } else {
        try {
            id = issueId?.toString()?.toInteger()
        } catch (Exception ignored) {
            id = null
        }
    }
    if (id != null && (id == forwardIssueId || id == reverseIssueId)) {
        flowDirection = (id == forwardIssueId) ? 'FORWARD' : 'REVERSE'
        questionsKey = "fake_" + flowDirection.toLowerCase() + "_" + undeliveredType.toLowerCase()
    }
}

return [
        flowDirection  : flowDirection,
        undeliveredType: undeliveredType,
        issueId        : issueId,
        questions      : questionsKey != null ? questionnaireConfig?.get(questionsKey) : null
]
