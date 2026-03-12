/**
 * prepare_elixir_thread.groovy
 *
 * Builds a thread entry for the incident update based on the Elixir action callback.
 *
 * Thread text resolution:
 *   - If action is not null: enum key elixir.action.threadText.<action>
 *   - If action is null:     enum key elixir.updates.threadText.<reasonCode>.<subreasonCode>
 *   - If resolved text is empty, builds a fallback from all context fields.
 *
 * createdByUser is set to persona from context.
 *
 * Reads from _global.nodeParameters: action, context
 * Returns: [threads: [...]]  (list with one thread entry)
 */

def action = _global?.nodeParameters?.action?.toString()?.trim()
def context = _global?.nodeParameters?.context

def reasonCode = context?.reasonCode?.toString()?.trim() ?: ''
def subreasonCode = context?.subreasonCode?.toString()?.trim() ?: ''
def reasonText = context?.reasonText?.toString()?.trim() ?: ''
def subReasonText = context?.subReasonText?.toString()?.trim() ?: ''
def persona = context?.persona?.toString()?.trim() ?: ''

def defaultFallbackText = [
        "Persona: ${persona}",
        "Reason Code: ${reasonCode}",
        "Reason Text: ${reasonText}",
        "Sub Reason Code: ${subreasonCode}",
        "Sub Reason Text: ${subReasonText}",
].join('\n')

def threadText = null

if (action) {
    def enumKey = 'elixir.action.threadText.' + action
    def enumValue = _enum_store?.get(enumKey)?.toString()?.trim()
    threadText = (enumValue != null && !enumValue.isEmpty()) ? enumValue : defaultFallbackText
} else {
    def enumKey = 'elixir.updates.threadText.' + reasonCode + '.' + subreasonCode
    def enumValue = _enum_store?.get(enumKey)?.toString()?.trim()
    threadText = (enumValue != null && !enumValue.isEmpty()) ? enumValue : defaultFallbackText
}

def thread = [
        text           : threadText,
        contentType    : 'text/plain',
        threadEntryType: [id: 30, name: null],
        createdByUser  : persona,
        action         : 'add'
]

return [threads: [thread]]
