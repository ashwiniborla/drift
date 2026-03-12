/**
 * prepare_elixir_action_update.groovy
 *
 * Prepares the incident update body based on the elixir action callback.
 *
 * Actions are categorised into two types via actionTypeMap:
 *   - 'action'  (e.g. ALT_PH_NUMBER_REQUIRED) → enum key: elixir.action.threadText.<actionName>
 *   - 'others'  (default, e.g. OTHERS)         → enum key: elixir.updates.threadText.<reasonCode>.<subreasonCode>
 *
 * CLOSED is handled separately: builds elxrTkt with status=CLOSED.
 *
 * Returns: [action, elxrTkt, threads, error]
 */

def actionTypeMap = [
    'ALT_PH_NUMBER_REQUIRED': 'action'
]

def action = null

try {
    def viewResponse = _global.get('elixir_waiting_for_updates:viewResponse')?.selectedOptions
    action = viewResponse?.action?.toString()?.trim()
    def context = viewResponse?.context

    if (!action) {
        return [action: null, elxrTkt: null, threads: null, error: true]
    }

    // --- CLOSED: build elxrTkt with status=CLOSED ---
    if (action == 'CLOSED') {
        def base = _global?.elixir_create_ticket?.elxrTkt
        if (base == null) {
            return [action: action, elxrTkt: null, threads: null, error: true]
        }

        def entity = base?.entity
        def entityCopy = (entity != null && entity instanceof Map)
            ? [referenceType: entity.referenceType, referenceId: entity.referenceId, type: entity.type]
            : null

        def elxrTkt = [
            id         : base?.id,
            workflowId : base?.workflowId,
            status     : 'CLOSED',
            type       : base?.type,
            entity     : entityCopy,
            createdAt  : base?.createdAt,
            updatedAt  : new Date()
        ]

        return [action: action, elxrTkt: elxrTkt, threads: null, error: false]
    }

    // --- Non-CLOSED: build thread ---
    def reasonCode    = context?.reasonCode?.toString()?.trim() ?: ''
    def subreasonCode = context?.subreasonCode?.toString()?.trim() ?: ''
    def reasonText    = context?.reasonText?.toString()?.trim() ?: ''
    def subReasonText = context?.subReasonText?.toString()?.trim() ?: ''
    def persona       = context?.persona?.toString()?.trim() ?: ''

    def defaultFallbackText = [
        "Reason Code: ${reasonCode}",
        "Reason Text: ${reasonText}",
        "Sub Reason Code: ${subreasonCode}",
        "Sub Reason Text: ${subReasonText}",
        "Persona: ${persona}"
    ].join('\n')

    // Use default thread text only (enum lookup for threadText removed)
    def threadText = defaultFallbackText

    def thread = [
        text           : threadText,
        contentType    : 'text/plain',
        threadEntryType: [id: 30, name: null],
        createdByUser  : persona,
        action         : 'add',
    ]

    return [action: action, elxrTkt: null, threads: [thread], error: false]

} catch (Exception e) {
    return [action: action, elxrTkt: null, threads: null, error: true]
}
