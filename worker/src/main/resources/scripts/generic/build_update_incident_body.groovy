/**
 * build_update_incident_body.groovy (generic)
 *
 * Builds an IncidentRequest body for updateIncidentV3 API.
 * Reusable: only includes optional fields when the corresponding node parameter is non-null.
 *
 * Context (via nodeParameters):
 *   _global.nodeParameters.incidentId         – required
 *   _global.nodeParameters.statusWithType     – optional; { id, name } e.g. from JsonPath
 *   _global.nodeParameters.statusId            – optional; when statusWithType is null, use this (string or number) as id, name = null
 *   _global.nodeParameters.questionnaireData  – optional; added as customFields.qsnareDta
 *   _global.nodeParameters.elxrTkt            – optional; added as customFields.elxrTkt (elixir ticket details)
 *   _global.nodeParameters.threads            – optional; list of incidentThreadRequest maps
 *   _global.nodeParameters.notesText          – optional; when threads is null, builds one thread with this text (default queue/threadEntryType)
 *   addV3ChildWorkflow when _global.nodeParameters.addChildWorkflow is true: build from _global + _enum_store
 *     workflowId: _global.workflowId; workflowName: _global.params.workflowId (throw if null); workflowVersion: _global.params.version
 *     isSmartWorkflow: _enum_store.childWorkflow.<workflowName>.isSmart (false if not configured)
 *     actionEligibility: _enum_store.childWorkflow.<workflowName>.actionEligibility (blank if not configured)
 *   Or when _global.nodeParameters.childWorkflowDetails is present (single object with all fields).
 *
 * Returns: Map matching IncidentRequest structure
 */

def incidentId = _global?.nodeParameters?.incidentId
if (incidentId == null || incidentId.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("incidentId is required to update incident.")
}

def incidentDataRequest = [
        id          : incidentId,
        incidentType: 'CS'
]

def statusWithType = _global?.nodeParameters?.statusWithType
def statusId = _global?.nodeParameters?.statusId
if (statusWithType != null) {
    incidentDataRequest['statusWithType'] = statusWithType
} else if (statusId != null && statusId.toString().trim().length() > 0) {
    incidentDataRequest['statusWithType'] = [id: statusId instanceof Number ? statusId : statusId.toString().trim().toInteger(), name: null]
}

def questionnaireData = _global?.nodeParameters?.questionnaireData
if (questionnaireData != null) {
    incidentDataRequest['incidentCustomFieldsRequest'] = [
            incidentId   : incidentId,
            customFields : [
                    qsnareDta: questionnaireData instanceof List ? questionnaireData : [questionnaireData]
            ]
    ]
}

def elxrTkt = _global?.nodeParameters?.elxrTkt
if (elxrTkt != null) {
    if (incidentDataRequest['incidentCustomFieldsRequest'] == null) {
        incidentDataRequest['incidentCustomFieldsRequest'] = [
            incidentId   : incidentId,
            customFields : [:]
        ]
    }
    incidentDataRequest['incidentCustomFieldsRequest']['customFields']['elxrTkt'] = elxrTkt
}

def threads = _global?.nodeParameters?.threads
def notesText = _global?.nodeParameters?.notesText
if (threads != null && threads instanceof List && !threads.isEmpty()) {
    incidentDataRequest['incidentThreadRequests'] = threads
} else if (notesText != null && notesText.toString().trim().length() > 0) {
    incidentDataRequest['incidentThreadRequests'] = [[
            queue         : [id: 17, name: 'Cs voice'],
            text          : notesText.toString().trim(),
            contentType   : 'text/plain',
            threadEntryType: [id: 4, name: null],
            action        : 'add'
    ]]
}

// addV3ChildWorkflow: when addChildWorkflow node param is true, build from _global + _enum_store; else when childWorkflowDetails object is present, use it
def addChildWorkflow = _global?.nodeParameters?.addChildWorkflow == "true"
def childWorkflowDetails = _global?.nodeParameters?.childWorkflowDetails

if (addChildWorkflow) {
    def workflowName = _global?.params?.workflowId
    if (workflowName == null || workflowName.toString().trim().isEmpty()) {
        throw new IllegalArgumentException("params.workflowId is required when addChildWorkflow is true (used as workflowName).")
    }
    def workflowId = _global?.workflowId
    if (workflowId == null || workflowId.toString().trim().isEmpty()) {
        throw new IllegalArgumentException("workflowId is required when addChildWorkflow is true.")
    }
    def workflowVersion = _global?.params?.version
    if (workflowVersion == null || workflowVersion.toString().trim().isEmpty()) {
        throw new IllegalArgumentException("params.version is required when addChildWorkflow is true.")
    }
    def workflowNameStr = workflowName.toString().trim()
    def isSmartKey = 'childWorkflow.' + workflowNameStr + '.isSmart'
    def isSmartVal = _enum_store?.get(isSmartKey)
    def isSmartWorkflow = (isSmartVal != null && isSmartVal.toString().trim().length() > 0) ? Boolean.valueOf(isSmartVal.toString().trim()) : false
    def actionEligibilityKey = 'childWorkflow.' + workflowNameStr + '.actionEligibility'
    def actionEligibilityVal = _enum_store?.get(actionEligibilityKey)
    def actionEligibility = (actionEligibilityVal != null && actionEligibilityVal.toString().trim().length() > 0) ? actionEligibilityVal.toString().trim() : ''
    incidentDataRequest['v3ChildWorkflowRequest'] = [
            workflowId        : workflowId.toString().trim(),
            isSmartWorkflow   : isSmartWorkflow,
            actionEligibility : (actionEligibility != null && !actionEligibility.isEmpty()) ? actionEligibility : null,
            workflowName      : workflowNameStr,
            workflowVersion   : workflowVersion.toString().trim()
    ]
} else if (childWorkflowDetails != null) {
    def workflowId = childWorkflowDetails?.workflowId
    if (workflowId == null || workflowId.toString().trim().isEmpty()) {
        throw new IllegalArgumentException("childWorkflowDetails.workflowId is required for v3ChildWorkflowRequest.")
    }
    def workflowName = childWorkflowDetails?.workflowName
    if (workflowName == null || workflowName.toString().trim().isEmpty()) {
        throw new IllegalArgumentException("childWorkflowDetails.workflowName is required for v3ChildWorkflowRequest.")
    }
    def workflowVersion = childWorkflowDetails?.workflowVersion
    if (workflowVersion == null || workflowVersion.toString().trim().isEmpty()) {
        throw new IllegalArgumentException("childWorkflowDetails.workflowVersion is required for v3ChildWorkflowRequest.")
    }
    def isSmartWorkflowParam = childWorkflowDetails?.isSmartWorkflow
    if (isSmartWorkflowParam == null) {
        throw new IllegalArgumentException("childWorkflowDetails.isSmartWorkflow is required for v3ChildWorkflowRequest and cannot be null.")
    }
    def isSmartWorkflow = (isSmartWorkflowParam instanceof Boolean) ? isSmartWorkflowParam : Boolean.valueOf(isSmartWorkflowParam?.toString())
    def actionEligibility = childWorkflowDetails?.actionEligibility
    incidentDataRequest['v3ChildWorkflowRequest'] = [
            workflowId        : workflowId.toString().trim(),
            isSmartWorkflow   : isSmartWorkflow,
            actionEligibility : actionEligibility != null ? actionEligibility.toString().trim() : null,
            workflowName      : workflowName.toString().trim(),
            workflowVersion   : workflowVersion.toString().trim()
    ]
}

return [
        incidentType       : 'CS',
        incidentDataRequest : incidentDataRequest
]
