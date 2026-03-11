/**
 * validate_update_params.groovy
 *
 * Validates required params for the elixir update workflow.
 * Throws IllegalArgumentException on missing required fields.
 *
 * Required: incidentId, statusUpdate
 * Required when statusUpdate=CLOSED: elxrTkt
 * Optional: action, context
 */

def incidentId = _global?.nodeParameters?.incidentId?.toString()?.trim()
if (!incidentId) {
    throw new IllegalArgumentException("incidentId is required for update workflow; got null or empty.")
}

def statusUpdate = _global?.nodeParameters?.statusUpdate?.toString()?.trim()
if (!statusUpdate) {
    throw new IllegalArgumentException("statusUpdate is required for update workflow; got null or empty.")
}

if (statusUpdate != 'CLOSED' && statusUpdate != 'ACTION') {
    throw new IllegalArgumentException("statusUpdate must be CLOSED or ACTION; got: ${statusUpdate}")
}

if (statusUpdate == 'CLOSED') {
    def elxrTkt = _global?.nodeParameters?.elxrTkt
    if (elxrTkt == null || !(elxrTkt instanceof Map)) {
        throw new IllegalArgumentException("elxrTkt (Map) is required when statusUpdate=CLOSED; got null or non-Map.")
    }
}

return [validated: true]
