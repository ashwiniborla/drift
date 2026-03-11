/**
 * validate_acceptance_params.groovy
 *
 * Validates that all required params are present for the acceptance workflow.
 * Throws IllegalArgumentException on missing required fields so the workflow
 * fails fast at the entry node.
 *
 * Required nodeParameters: incidentId, status, elxrTkt
 * Optional nodeParameters: ticketId, createdAt, message
 */

def incidentId = _global?.nodeParameters?.incidentId?.toString()?.trim()
if (!incidentId) {
    throw new IllegalArgumentException("incidentId is required for acceptance workflow; got null or empty.")
}

def status = _global?.nodeParameters?.status?.toString()?.trim()
if (!status) {
    throw new IllegalArgumentException("status is required for acceptance workflow; got null or empty.")
}

if (status != 'CREATED' && status != 'REJECTED') {
    throw new IllegalArgumentException("status must be CREATED or REJECTED; got: ${status}")
}

def elxrTkt = _global?.nodeParameters?.elxrTkt
if (elxrTkt == null || !(elxrTkt instanceof Map)) {
    throw new IllegalArgumentException("elxrTkt (Map) is required for acceptance workflow; got null or non-Map.")
}

return [validated: true]
