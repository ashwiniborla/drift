/**
 * prepare_elixir_action_update.groovy
 *
 * Prepares the incident update body based on the elixir action callback.
 *
 * Actions are categorised into two types via actionTypeMap:
 *   - 'action'  (e.g. ALT_PH_NUMBER_REQUIRED) → triggers a child workflow add
 *   - 'others'  (default, e.g. OTHERS)         → thread-only update
 *
 * CLOSED is handled separately: builds elxrTkt with status=CLOSED.
 *
 * For ALT_PH_NUMBER_REQUIRED, a child workflow is registered:
 *   childWfAction='add', childWorkflowName='forward_address_phone_change_smart', childWorkflowVersion='SNAPSHOT'
 *
 * Returns: a single Map { action, elxrTkt, threads, error, childWfAction, childWorkflowName, childWorkflowVersion }
 */

def actionTypeMap = [
    'ALT_PH_NUMBER_REQUIRED': 'action'
]

def childWorkflowMap = [
    'ALT_PH_NUMBER_REQUIRED': [
        childWfAction       : 'add',
        childWorkflowName   : 'forward_address_phone_change_smart',
        childWorkflowVersion: 'SNAPSHOT'
    ]
]

def result = [
    action              : null,
    elxrTkt             : null,
    threads             : null,
    error               : false,
    childWfAction       : null,
    childWorkflowName   : null,
    childWorkflowVersion: null
]

try {
    def viewResponse = _global.get('elixir_waiting_for_updates:viewResponse')?.selectedOptions
    def action = viewResponse?.action?.toString()?.trim()
    result.action = action

    if (!action) {
        result.error = true
        return result
    }

    // --- CLOSED: build elxrTkt with status=CLOSED ---
    if (action == 'CLOSED') {
        def base = _global?.elixir_create_ticket?.elxrTkt
        if (base == null) {
            result.error = true
            return result
        }

        def entity = base?.entity
        def entityCopy = (entity != null && entity instanceof Map)
            ? [referenceType: entity.referenceType, referenceId: entity.referenceId, type: entity.type]
            : null

        result.elxrTkt = [
            id        : base?.id,
            workflowId: base?.workflowId,
            status    : 'CLOSED',
            type      : base?.type,
            entity    : entityCopy,
            createdAt : base?.createdAt,
            updatedAt : new Date()
        ]
        return result
    }

    // --- Non-CLOSED: build thread ---
    def context       = viewResponse?.context
    def reasonCode    = context?.reasonCode?.toString()?.trim() ?: ''
    def subreasonCode = context?.subreasonCode?.toString()?.trim() ?: ''
    def reasonText    = context?.reasonText?.toString()?.trim() ?: ''
    def subReasonText = context?.subReasonText?.toString()?.trim() ?: ''
    def persona       = context?.persona?.toString()?.trim() ?: ''

    def threadText = [
        "Reason Code: ${reasonCode}",
        "Reason Text: ${reasonText}",
        "Sub Reason Code: ${subreasonCode}",
        "Sub Reason Text: ${subReasonText}",
        "Persona: ${persona}"
    ].join('\n')

    result.threads = [[
        text           : threadText,
        contentType    : 'text/plain',
        threadEntryType: [id: 30, name: null],
        createdByUser  : persona,
        action         : 'add'
    ]]

    // --- Action-type actions: register a child workflow ---
    if (actionTypeMap[action] == 'action') {
        def childWf = childWorkflowMap[action]
        if (childWf != null) {
            result.childWfAction        = childWf.childWfAction
            result.childWorkflowName    = childWf.childWorkflowName
            result.childWorkflowVersion = childWf.childWorkflowVersion
        }
    }

} catch (Exception e) {
    result.error = true
}

return result
