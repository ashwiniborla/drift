/**
 * build_update_incident_body.groovy (generic)
 *
 * Builds an IncidentRequest body for updateIncidentV3 API.
 * Reusable: only includes optional fields when the corresponding node parameter is non-null.
 *
 * Context (via nodeParameters):
 *   _global.nodeParameters.incidentId             – required
 *   _global.nodeParameters.statusWithType         – optional; { id, name } e.g. from JsonPath
 *   _global.nodeParameters.statusId               – optional; when statusWithType is null, use this (string or number) as id, name = null
 *   _global.nodeParameters.questionnaireData      – optional; added as customFields.qsnareDta
 *   _global.nodeParameters.elxrTkt                – optional; added as customFields.elxrTkt (elixir ticket details)
 *   _global.nodeParameters.threads                – optional; list of incidentThreadRequest maps
 *   _global.nodeParameters.notesText              – optional; when threads is null, builds one thread with this text (default queue/threadEntryType)
 *
 *   v3ChildWorkflowRequest — triggered only when childWorkflowAction is present:
 *     _global.nodeParameters.childWorkflowAction    – "add" or "update"; presence is the sole trigger for v3ChildWorkflowRequest
 *     _global.nodeParameters.childWorkflowMeta      – optional; when present, used as the entire v3ChildWorkflowRequest object as-is
 *     When childWorkflowMeta is NOT present:
 *       _global.nodeParameters.workflowId           – required; maps to meta.workflowId
 *       _global.nodeParameters.childWorkflowName    – required; maps to top-level workflowName
 *       _global.nodeParameters.childWorkflowVersion – required; maps to top-level workflowVersion
 *       For action "add":
 *         isSmartWorkflow: _enum_store.childWorkflow.<childWorkflowName>.isSmart (false if not configured)
 *         actionEligibility: _enum_store.childWorkflow.<childWorkflowName>.actionEligibility (null if not configured)
 *       For action "update":
 *         _global.nodeParameters.childWorkflowCompleted – optional boolean; maps to meta.isCompleted
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

// v3ChildWorkflowRequest: triggered only when childWorkflowAction is present
def childWorkflowAction = _global?.nodeParameters?.childWorkflowAction?.toString()?.trim()
def childWorkflowMeta = _global?.nodeParameters?.childWorkflowMeta

if (childWorkflowAction != null && !childWorkflowAction.isEmpty()) {
    if (childWorkflowMeta != null) {
        // Branch 1: full object supplied by caller — use as-is
        incidentDataRequest['v3ChildWorkflowRequest'] = childWorkflowMeta
    } else if (childWorkflowAction == 'add') {
        // Branch 2: build AddMeta from node params + enum store
        def workflowId = _global?.nodeParameters?.workflowId
        def childWorkflowName = _global?.nodeParameters?.childWorkflowName
        if (childWorkflowName == null || childWorkflowName.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("nodeParameters.childWorkflowName is required when childWorkflowAction is 'add'.")
        }
        def childWorkflowVersion = _global?.nodeParameters?.childWorkflowVersion
        if (childWorkflowVersion == null || childWorkflowVersion.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("nodeParameters.childWorkflowVersion is required when childWorkflowAction is 'add'.")
        }
        def workflowNameStr = childWorkflowName.toString().trim()
        def workflowConfig = _enum_store?.childWorkflow?.get(workflowNameStr)
        def isSmartVal = workflowConfig?.isSmart
        def isSmartWorkflow = (isSmartVal != null && isSmartVal.toString().trim().length() > 0) ? Boolean.valueOf(isSmartVal.toString().trim()) : false
        def actionEligibilityVal = workflowConfig?.actionEligibility
        def actionEligibility = (actionEligibilityVal != null && actionEligibilityVal.toString().trim().length() > 0) ? actionEligibilityVal.toString().trim() : null
        incidentDataRequest['v3ChildWorkflowRequest'] = [
                meta            : [
                        workflowId        : workflowId.toString().trim(),
                        isSmartWorkflow   : isSmartWorkflow,
                        actionEligibility : actionEligibility,
                        action            : 'add'
                ],
                workflowName    : workflowNameStr,
                workflowVersion : childWorkflowVersion.toString().trim()
        ]
    } else if (childWorkflowAction == 'update') {
        // Branch 3: build UpdateMeta from node params
        def workflowId = _global?.nodeParameters?.workflowId
        if (workflowId == null || workflowId.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("nodeParameters.workflowId is required when childWorkflowAction is 'update'.")
        }
        def childWorkflowName = _global?.nodeParameters?.childWorkflowName
        if (childWorkflowName == null || childWorkflowName.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("nodeParameters.childWorkflowName is required when childWorkflowAction is 'update'.")
        }
        def childWorkflowVersion = _global?.nodeParameters?.childWorkflowVersion
        if (childWorkflowVersion == null || childWorkflowVersion.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("nodeParameters.childWorkflowVersion is required when childWorkflowAction is 'update'.")
        }
        def childWorkflowCompleted = _global?.nodeParameters?.childWorkflowCompleted
        def isCompleted = (childWorkflowCompleted != null) ? (childWorkflowCompleted instanceof Boolean ? childWorkflowCompleted : Boolean.valueOf(childWorkflowCompleted.toString())) : null
        def meta = [
                workflowId : workflowId.toString().trim(),
                action     : 'update'
        ]
        if (isCompleted != null) {
            meta['isCompleted'] = isCompleted
        }
        incidentDataRequest['v3ChildWorkflowRequest'] = [
                meta            : meta,
                workflowName    : childWorkflowName.toString().trim(),
                workflowVersion : childWorkflowVersion.toString().trim()
        ]
    } else {
        throw new IllegalArgumentException("Unsupported childWorkflowAction: '${childWorkflowAction}'. Supported values: 'add', 'update'.")
    }
}

return [
        incidentType       : 'CS',
        incidentDataRequest : incidentDataRequest
]
