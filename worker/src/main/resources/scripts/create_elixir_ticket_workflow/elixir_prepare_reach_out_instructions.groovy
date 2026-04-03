/**
 * elixir_prepare_reach_out_instructions.groovy
 *
 * Builds inputs for push_to_varadhi (topic) after Elixir update (OTHERS path):
 * topic name from enum, RESTBUS headers, and reach-out JSON body.
 *
 * Reads:
 *   elixir_waiting_for_updates:viewResponse (reason/subreason/persona)
 *   prepare_elixir_action_update.action (body.action, X_EVENT_NAME branch)
 *   customer, orderDetails[0], fetch_order_oxford (account_id fallback)
 *
 * Returns: topicName, body, extraHeaders, groupId (null), httpUri (null), method (POST)
 */

def viewSelected = _global.get('elixir_waiting_for_updates:viewResponse')?.selectedOptions
if (viewSelected == null) {
    throw new IllegalStateException('elixir_waiting_for_updates:viewResponse.selectedOptions is required for reach-out topic publish.')
}

def context = viewSelected.context
if (context == null) {
    throw new IllegalStateException('viewResponse.selectedOptions.context is required for reach-out topic publish.')
}

def reasonCode = context?.reason_code?.toString()?.trim() ?: ''
def subreasonCode = context?.subreason_code?.toString()?.trim() ?: ''
def reasonSubReasonKey = reasonCode
if (subreasonCode) {
    reasonSubReasonKey += "_${subreasonCode}"
}

def persona = context?.persona?.toString()?.trim() ?: ''
if (!persona) {
    throw new IllegalArgumentException('context.persona is required for reach-out body.')
}

def prepareAction = _global?.prepare_elixir_action_update?.action?.toString()?.trim()
if (!prepareAction) {
    throw new IllegalArgumentException('prepare_elixir_action_update.action is required for reach-out body.')
}

def topicName = _enum_store?.get('reachout.elixirCommunicationTopic')?.toString()?.trim()
if (!topicName) {
    throw new IllegalArgumentException("enum key 'reachout.elixirCommunicationTopic' is missing or empty.")
}

def firstOrder = _global?.orderDetails?.getAt(0)
if (firstOrder == null) {
    throw new IllegalStateException('orderDetails[0] is required for reach-out body.')
}

def orderId = firstOrder?.orderId?.toString()?.trim()
def unitId = firstOrder?.orderItemUnitId?.toString()?.trim()
def trackingId = firstOrder?.trackingId?.toString()?.trim()
if (!orderId || !unitId || !trackingId) {
    throw new IllegalArgumentException('orderDetails[0] must include orderId, orderItemUnitId, and trackingId.')
}

def accountId = (_global?.customer?.customerId ?: '').toString().trim()
if (!accountId) {
    def raw = _global?.fetch_order_oxford
    def dataVar = 'v2OrderData_imsv2_varadhi_client1_default'
    accountId = raw?.resolvedVariablesResponse?.ORDER?.pivotIdContextMap?.get(orderId)?.resolvedVariables?.get(dataVar)?.value?.oms3_aggregated_order?.oms3_order_data?.accountId?.toString()?.trim() ?: ''
}
if (!accountId) {
    throw new IllegalArgumentException('account_id could not be resolved from customer.customerId or fetch_order_oxford.')
}

def eventName = (prepareAction == 'ALT_PH_NUMBER_REQUIRED') ? 'ELIXIR_ACTIONABLE_EVENT' : 'ELIXIR_NONACTIONABLE_EVENT'
def flowType = _global.prepare_create_ticket_details?.body?.issue_type

def extraHeaders = [
        'X-TENANT-ID'       : 'imsv3-worker',
        'X_REASON_SUBREASON': reasonSubReasonKey,
        'X_EVENT_NAME'      : eventName,
        'X_FLOW_TYPE'       : flowType
]

def body = [
        account_id: accountId,
        order_id  : orderId,
        unit_id   : unitId,
        action    : prepareAction,
        reason    : reasonCode,
        subreason : subreasonCode,
        trackingId: trackingId,
        persona   : persona,
        flow_type : flowType
]

return [
        topicName   : topicName,
        body        : body,
        extraHeaders: extraHeaders,
        groupId     : null,
        httpUri     : null,
        method      : 'POST',
]
