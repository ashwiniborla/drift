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
def issueConfig = _enum_store?.questionnaire_config?.issue
def undeliveredType = global?.nodeParameters?.undeliveredType != null ? global.nodeParameters.undeliveredType.toString().trim() : null
if (undeliveredType == null || undeliveredType.isEmpty()) {
    throw new IllegalArgumentException("undeliveredType is required for the fake questionnaire workflow. Please provide a non-empty value (e.g. rfr, cnr).")
}

if (issueId == null || issueId?.isEmpty()) {
    throw new IllegalArgumentException("Issue Id is required to start")
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
def questionnaireType = null;
def config = issueConfig.get(issueId?.toString())
if (issueId != null) {
    if (issueConfig.containsKey(issueId?.toString())) {
        flowDirection = config.flowDirection
        questionnaireType = "fake_" + flowDirection.toLowerCase() + "_" + undeliveredType.toLowerCase()
    }
}

return [
        undeliveredType  : undeliveredType,
        questions        : questionnaireType != null ? questionnaireConfig?.get(questionnaireType) : null,
        issueConfig      : config,
        questionnaireType: questionnaireType
]
