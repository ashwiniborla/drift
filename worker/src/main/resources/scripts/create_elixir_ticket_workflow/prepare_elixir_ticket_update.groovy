/**
 * prepare_elixir_ticket_update.groovy
 *
 * Prepares Varadhi push inputs for POST /elixir/v1/tickets/{ticketId}/updates (COMMUNICATION).
 * Reads reason/subreason from _enum_store.elixir.actionConfig[updateType].responseConfig.
 *
 * Node parameters:
 *   elixirTicketId (required to send), updateType (e.g. ALT_PH_NUMBER_REQUIRED),
 *   optional: persona (default CX), persona_id, asset_type, id_type
 *   elixirTicketId source is workflow-defined (e.g. $.params.elixirTicketId or, for
 *   forward_address_phone_change_smart, $.get_incident_for_elixir.elixirTicketId).
 *
 * If action config or responseConfig is missing, or ticket id is empty, returns
 * sendElixirCommunicationUpdate: false and skips push (workflow BRANCH).
 */

def skipResult = [
        sendElixirCommunicationUpdate: false,
        body                         : null,
        extraHeaders                 : null,
        httpUri                      : null,
        method                       : null,
        groupId                      : null,
        messageId                    : null,
        queueName                    : null,
]

def elixirTicketId = _global?.nodeParameters?.elixirTicketId?.toString()?.trim()
def updateType = _global?.nodeParameters?.updateType?.toString()?.trim()

if (!elixirTicketId || !updateType) {
    return skipResult
}

def actionCfg = _enum_store?.elixir?.actionConfig?.get(updateType)
def responseConfig = actionCfg?.responseConfig
if (actionCfg == null || !(responseConfig instanceof Map)) {
    return skipResult
}

def reasonCode = responseConfig?.reasonCode?.toString()?.trim()
if (!reasonCode) {
    return skipResult
}

def elixirHost = (_enum_store?.clients?.get('elixir.ch.host') ?: '').toString().trim()
def queueName = (_enum_store?.elixir?.get('publishQueue') ?: '').toString().trim()
if (!elixirHost || !queueName) {
    throw new IllegalArgumentException("elixir.ch.host and elixir.publishQueue are required when sending Elixir ticket update.")
}

def subReasonCode = responseConfig?.subReasonCode
def reasonText = responseConfig?.reasonText != null ? responseConfig.reasonText.toString() : ''
def subReasonText = responseConfig?.subReasonText != null ? responseConfig.subReasonText.toString() : ''

def tz = java.util.TimeZone.getTimeZone('Asia/Kolkata')
def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
sdf.setTimeZone(tz)
def updatedAt = sdf.format(new Date())

def personaParam = _global?.nodeParameters?.persona
def persona = (personaParam != null && personaParam.toString().trim()) ? personaParam.toString().trim() : 'CX'

def personaId = _global?.nodeParameters?.persona_id
def assetType = _global?.nodeParameters?.asset_type
def idType = _global?.nodeParameters?.id_type

def body = [
        action_type   : 'COMMUNICATION',
        persona       : persona,
        asset_type    : assetType,
        id_type       : idType,
        persona_id    : personaId,
        action_context: [
                reason_code   : reasonCode,
                subreason_code: subReasonCode,
                reason_text   : reasonText,
                subreason_text: subReasonText,
                updated_at    : updatedAt,
        ],
]

def uuid = java.util.UUID.randomUUID().toString()
def random4 = String.format('%04d', new Random().nextInt(10000))
def messageId = elixirTicketId + random4

def extraHeaders = [
        'X_CLIENT_ID' : 'CX',
        'X_REQUEST_ID': uuid,
        'X_TENANT_ID' : 'ELIXIR_FK',
]

def httpUri = elixirHost + '/elixir/v1/tickets/' + elixirTicketId + '/updates'

return [
        sendElixirCommunicationUpdate: true,
        body                         : body,
        extraHeaders                 : extraHeaders,
        httpUri                      : httpUri,
        method                       : 'POST',
        groupId                      : elixirTicketId,
        messageId                    : messageId,
        queueName                    : queueName,
]
