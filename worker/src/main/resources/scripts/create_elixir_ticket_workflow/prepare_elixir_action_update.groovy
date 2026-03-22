/**
 * prepare_elixir_action_update.groovy
 *
 * Prepares the incident update body based on the elixir action callback.
 *
 * CLOSED is handled separately: builds elxrTkt with status=CLOSED.
 *
 * For ALT_PH_NUMBER_REQUIRED, a child workflow is registered:
 *   childWfAction='add', childWorkflowName='forward_address_phone_change_smart', childWorkflowVersion='SNAPSHOT'
 *
 * Returns: a single Map { action, elxrTkt, threads, error, childWfAction, childWorkflowName, childWorkflowVersion }
 */

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
    def threadText;
    def persona;
    def status = viewResponse?.status
    result.action = action

    if (!action && !status) {
        result.error = true
        return result
    }
    // --- CLOSED: build elxrTkt with status=CLOSED ---
    if (status == 'CLOSED') {
        result.action = 'CLOSED'
        def base = _global?.prepare_create_ticket_details?.elxrTkt
        if (base == null) {
            result.error = true
            return result
        }

        def entity = base?.entity
        def entityCopy = (entity != null && entity instanceof Map)
            ? [referenceType: entity.referenceType, referenceId: entity.referenceId, type: entity.type]
            : null

        threadText = "Elixir ticket closed"
        persona = "Elixir Team"

        result.elxrTkt = [
            id        : base?.id,
            workflowId: base?.workflowId,
            status    : 'CLOSED',
            type      : base?.type,
            entity    : entityCopy,
            createdAt : base?.createdAt,
            updatedAt : new Date()
        ]
    } else {

        // --- Non-CLOSED: build thread ---
        def context = viewResponse?.context
        def reasonCode = context?.reason_code?.toString()?.trim() ?: ''
        def subreasonCode = context?.subreason_code?.toString()?.trim() ?: ''
        def reasonText = context?.reason_text?.toString()?.trim() ?: ''
        def subReasonText = context?.subreason_text?.toString()?.trim() ?: ''
        persona = context?.persona?.toString()?.trim() ?: ''

        def defaultFallbackText = [
                "Reason Code: ${reasonCode}",
                "Reason Text: ${reasonText}",
                "Sub Reason Code: ${subreasonCode}",
                "Sub Reason Text: ${subReasonText}",
                "Persona: ${persona}"
        ].join('\n')

        if (childWorkflowMap.get(action) != null) {
            def enumKey = 'elixir.action.threadText.' + action
            def enumValue = _enum_store?.get(enumKey)?.toString()?.trim()
            threadText = (enumValue != null && !enumValue.isEmpty()) ? enumValue : defaultFallbackText
        } else {
            def enumKey = 'elixir.updates.threadText.' + reasonCode + '.' + subreasonCode
            def enumValue = _enum_store?.get(enumKey)?.toString()?.trim()
            threadText = (enumValue != null && !enumValue.isEmpty()) ? enumValue : defaultFallbackText
        }
    }

    result.threads = [[
        text           : threadText,
        contentType    : 'text/plain',
        threadEntryType: [id: 30, name: null],
        createdByUser  : persona,
        action         : 'add'
    ]]

    // --- Action-type actions: register a child workflow ---
    if (childWorkflowMap.get(action) != null) {
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
