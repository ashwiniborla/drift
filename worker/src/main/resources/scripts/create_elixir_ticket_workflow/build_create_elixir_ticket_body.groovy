/**
 * Builds request body for Elixir create ticket API (POST /elixir/v1/tickets).
 * All params from _global.nodeParameters; issue_type and callback_url from enum store.
 * Throws if any required param is null/empty or enum lookup fails.
 * Enum keys (no "global." prefix): elixir.issueConfig.<issueId>.issue, elixir.callbackUrl
 */
def incidentId = _global?.nodeParameters?.incidentId?.toString()?.trim()
if (!incidentId) {
    throw new IllegalArgumentException("incidentId is required for create elixir ticket; got null or empty.")
}

def issueId = _global?.nodeParameters?.issueId?.toString()?.trim()
if (!issueId) {
    throw new IllegalArgumentException("issueId is required for create elixir ticket; got null or empty.")
}

def entityRefType = _global?.nodeParameters?.elixirEntityReferenceType?.toString()?.trim()
if (!entityRefType) {
    throw new IllegalArgumentException("elixirEntityReferenceType is required for create elixir ticket; got null or empty.")
}

def entityRefId = _global?.nodeParameters?.elixirEntityReferenceId?.toString()?.trim()
if (!entityRefId) {
    throw new IllegalArgumentException("elixirEntityReferenceId is required for create elixir ticket; got null or empty.")
}

def entityType = _global?.nodeParameters?.elixirEntityType?.toString()?.trim()
if (!entityType) {
    throw new IllegalArgumentException("elixirEntityType is required for create elixir ticket; got null or empty.")
}

def entityFlow = _global?.nodeParameters?.elixirEntityFlow?.toString()?.trim()
if (!entityFlow) {
    throw new IllegalArgumentException("elixirEntityFlow is required for create elixir ticket; got null or empty.")
}

def issueType = _enum_store?.elixir?.issueConfig?.get(issueId)?.issue
if (issueType == null || issueType.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("issue_type not found in enum store for key: elixir.issueConfig." + issueId + ".issue")
}
issueType = issueType.toString().trim()

def callbackUrl = _enum_store?.elixir?.callbackUrl
if (callbackUrl == null || callbackUrl.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("elixir.callbackUrl not found in enum store or empty.")
}
callbackUrl = callbackUrl.toString().trim()

return [
    external_id      : incidentId,
    issue_type       : issueType,
    entity_reference : [type: entityRefType, id: entityRefId],
    entity_type      : entityType,
    entity_flow      : entityFlow,
    callback_url     : callbackUrl,
    message          : null
]
