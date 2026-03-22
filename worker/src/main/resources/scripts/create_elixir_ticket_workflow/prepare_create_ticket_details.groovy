/**
 * prepare_create_ticket_details.groovy
 *
 * Prepares all inputs needed to push an Elixir create-ticket request to the Varadhi queue
 * via the generic push_to_varadhi_queue node, and builds the elxrTkt map for the
 * subsequent update_incident_elixir_requested node.
 *
 * Replaces: build_create_elixir_ticket_body.groovy (body) +
 *           build_elixir_ticket_transformer.groovy (elxrTkt)
 *
 * Reads from nodeParameters:
 *   incidentId, issueId, elixirEntityReferenceType, elixirEntityReferenceId,
 *   elixirEntityType, workflowId
 *
 * Reads from _enum_store (no "global." prefix):
 *   elixir.issueConfig.<issueId>.issue       – issue type
 *   elixir.issueConfig.<issueId>.flowDirection – entity flow
 *   elixir.callbackUrl                        – callback URL
 *   clients['elixir.ch.host']                 – Elixir API base host
 *   elixir['publishQueue']                    – Varadhi queue name
 *
 * Returns: Map with keys for push_to_varadhi_queue params + elxrTkt for update_incident
 */

// --- Required parameter validation ---

def incidentId = _global?.nodeParameters?.incidentId?.toString()?.trim()
if (!incidentId) {
    throw new IllegalArgumentException("incidentId is required for prepare_create_ticket_details; got null or empty.")
}

def issueId = _global?.nodeParameters?.issueId?.toString()?.trim()
if (!issueId) {
    throw new IllegalArgumentException("issueId is required for prepare_create_ticket_details; got null or empty.")
}

def entityRefType = _global?.nodeParameters?.elixirEntityReferenceType?.toString()?.trim()
if (!entityRefType) {
    throw new IllegalArgumentException("elixirEntityReferenceType is required for prepare_create_ticket_details; got null or empty.")
}

def entityRefId = _global?.nodeParameters?.elixirEntityReferenceId?.toString()?.trim()
if (!entityRefId) {
    throw new IllegalArgumentException("elixirEntityReferenceId is required for prepare_create_ticket_details; got null or empty.")
}

def entityType = _global?.nodeParameters?.elixirEntityType?.toString()?.trim()
if (!entityType) {
    throw new IllegalArgumentException("elixirEntityType is required for prepare_create_ticket_details; got null or empty.")
}

def workflowId = _global?.nodeParameters?.workflowId?.toString()?.trim()
if (!workflowId) {
    throw new IllegalArgumentException("workflowId is required for prepare_create_ticket_details; got null or empty.")
}

// --- Enum store lookups ---

def issueConfig = _enum_store?.elixir?.issueConfig?.get(issueId)

def issueType = issueConfig?.issue
if (issueType == null || issueType.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("issue_type not found in enum store for key: elixir.issueConfig." + issueId + ".issue")
}
issueType = issueType.toString().trim()

def entityFlow = issueConfig?.flowDirection
if (!entityFlow) {
    throw new IllegalArgumentException("flowDirection not found in enum store for key: elixir.issueConfig." + issueId + ".flowDirection")
}
entityFlow = entityFlow.toString().trim()

def callbackUrl = _enum_store?.elixir?.callbackUrl
if (callbackUrl == null || callbackUrl.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("elixir.callbackUrl not found in enum store or is empty.")
}
callbackUrl = callbackUrl.toString().trim()

def elixirHost = (_enum_store?.clients?.get('elixir.ch.host') ?: '').toString().trim()
def httpUri = elixirHost + '/elixir/v1/tickets'

def queueName = (_enum_store?.elixir?.get('publishQueue') ?: '').toString().trim()
if (!queueName) {
    throw new IllegalArgumentException("elixir.publishQueue not found in enum store or is empty.")
}

// --- Varadhi queue header values ---

def uuid = java.util.UUID.randomUUID().toString()
def random4 = String.format('%04d', new Random().nextInt(10000))
def messageId = incidentId + random4

def extraHeaders = [
    'X_CLIENT_ID' : 'CX',
    'X_REQUEST_ID': uuid,
    'X_TENANT_ID' : 'ELIXIR_FK'
]

// --- Elixir ticket body ---

def body = [
    external_id      : incidentId,
    issue_type       : issueType,
    entity_reference : [type: entityRefType, id: entityRefId],
    entity_type      : entityType,
    entity_flow      : entityFlow,
    callback_url     : callbackUrl,
    id_type          : 'SYSTEM',
    persona_id       : 'system',
    message          : null
]

// --- elxrTkt: re-use existing ticket or build fresh ---

def firstElixirDetails = _global?.elixir_filter_incidents?.firstElixirDetails
def elxrTkt
def notesText

if (firstElixirDetails != null) {
    // Re-request path: copy existing ticket details and update workflowId
    def updatedElxrTkt = [:]
    if (firstElixirDetails instanceof Map) {
        updatedElxrTkt.putAll(firstElixirDetails)
    }
    updatedElxrTkt['workflowId'] = workflowId
    elxrTkt = updatedElxrTkt
    notesText = 'Elixir ticket re requested'
} else {
    // New ticket path: build fresh elxrTkt with status=REQUESTED
    def now = new Date()
    elxrTkt = [
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
    notesText = 'Elixir ticket requested'
}

return [
    // Inputs for push_to_varadhi_queue node parameters
    body        : body,
    extraHeaders: extraHeaders,
    httpUri     : httpUri,
    method      : 'POST',
    groupId     : incidentId,
    messageId   : messageId,
    queueName   : queueName,

    // Inputs for update_incident_elixir_requested node parameters
    elxrTkt     : elxrTkt,
    notesText   : notesText
]
