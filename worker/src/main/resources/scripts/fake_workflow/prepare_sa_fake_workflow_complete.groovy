/**
 * Builds inputOptions for sa_fake_workflow_complete (SA success screen after update_incident).
 *
 * Reads:
 *   _global.fake_check_similarity_and_create_incident.incident_id
 *
 * Returns: [ inputOptions: List ] for INSTRUCTION possibleDynamicValues via nodeParameters.inputOptions
 */

def incidentId = _global?.fake_check_similarity_and_create_incident?.incident_id?.toString()
if (!incidentId) {
    throw new Exception('incident_id missing — expected _global.fake_check_similarity_and_create_incident.incident_id')
}

def inputOptions = [
    [
        id         : 'sa_workflow_complete',
        description: 'SA Workflow complete',
        tags       : [values: ['sa.complete_workflow_component']],
        instructions: [
            [
                templateId       : 'workflow_success_instructions',
                templateVariables: [
                    incidentId: incidentId
                ]
            ]
        ]
    ]
]

return [inputOptions: inputOptions]
