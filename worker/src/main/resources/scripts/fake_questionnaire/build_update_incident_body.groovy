/**
 * build_update_incident_body.groovy
 *
 * Builds an IncidentRequest body for updateIncidentV3 API.
 * Sets status to solved (statusId = 2), adds questionnaireData in customFields.
 *
 * Context:
 *   _global.nodeParameters.questionnaireData  – { questionnaireType, response, createdAt }
 *
 * Returns: Map matching IncidentRequest structure
 */

def questionnaireData = _global?.nodeParameters?.questionnaireData ?: [:]

return [
        incidentType       : 'CS',
        incidentDataRequest: [
                id                         : _global.nodeParameters.incidentId,
                incidentType               : 'CS',
                statusWithType             : [id: 2, name: null],
                incidentCustomFieldsRequest: [
                        incidentId  : _global.nodeParameters.incidentId,
                        customFields: [

                                qsnareDta: [questionnaireData]

                        ]
                ]
        ]
]
