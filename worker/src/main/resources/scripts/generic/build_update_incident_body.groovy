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
 *   _global.nodeParameters.threads            – optional; list of incidentThreadRequest maps
 *   _global.nodeParameters.notesText          – optional; when threads is null, builds one thread with this text (default queue/threadEntryType)
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

return [
        incidentType       : 'CS',
        incidentDataRequest : incidentDataRequest
]
