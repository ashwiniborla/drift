/**
 * Post-execution transformer for elixir_create_ticket HTTP node.
 * Builds initial elxrTkt object for IMS incident customFields.elxrTkt (status=REQUESTED).
 * Reads from _global.nodeParameters and _enum_store; returns [ response: _response, elxrTkt: elxrTkt ].
 * Enum key (no "global."): elixir.issueConfig.<issueId>.issue
 */
def workflowId = _global?.nodeParameters?.workflowId?.toString()?.trim()
if (!workflowId) {
    throw new IllegalArgumentException("workflowId is required to build elxrTkt; got null or empty.")
}

def issueId = _global?.nodeParameters?.issueId?.toString()?.trim()
if (!issueId) {
    throw new IllegalArgumentException("issueId is required to build elxrTkt; got null or empty.")
}

def entityRefType = _global?.nodeParameters?.elixirEntityReferenceType?.toString()?.trim()
if (!entityRefType) {
    throw new IllegalArgumentException("elixirEntityReferenceType is required to build elxrTkt; got null or empty.")
}

def entityRefId = _global?.nodeParameters?.elixirEntityReferenceId?.toString()?.trim()
if (!entityRefId) {
    throw new IllegalArgumentException("elixirEntityReferenceId is required to build elxrTkt; got null or empty.")
}

def entityType = _global?.nodeParameters?.elixirEntityType?.toString()?.trim()
if (!entityType) {
    throw new IllegalArgumentException("elixirEntityType is required to build elxrTkt; got null or empty.")
}

def issueType = _enum_store?.elixir?.issueConfig?.get(issueId)?.issue
if (issueType == null || issueType.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("issue type not found in enum store for key: elixir.issueConfig." + issueId + ".issue")
}
issueType = issueType.toString().trim()

def now = new Date()
def elxrTkt = [
    id         : null,
    workflowId : workflowId,
    status     : 'REQUESTED',
    type       : issueType,
    entity     : [
        referenceType : entityRefType,
        referenceId   : entityRefId,
        type          : entityType
    ],
    createdAt  : now,
    updatedAt  : now
]

return [
    response : _response,
    elxrTkt   : elxrTkt
]
