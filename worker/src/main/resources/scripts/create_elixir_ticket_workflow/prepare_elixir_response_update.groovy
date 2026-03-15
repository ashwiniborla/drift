/**
 * Builds full elxrTkt for the second incident update (after callback).
 * Backend treats customFields.elxrTkt as a single object: sending only some fields replaces the entire
 * object and drops entity/workflowId/type etc. So we send the full object: initial elxrTkt from
 * elixir_create_ticket (entity, workflowId, type, createdAt) with id, status, updatedAt from callback.
 */
def base = _global?.elixir_create_ticket?.elxrTkt ?: _global.elixir_filter_incidents?.firstElixirDetails
if (base == null) {
    throw new IllegalArgumentException("elixir_create_ticket.elxrTkt not found in context; cannot build full elxrTkt update.")
}

def viewResponse = _global.get('elixir_waiting_for_response:viewResponse')?.selectedOptions

base.id = viewResponse?.ticket_id
base.status = viewResponse?.status
base.updatedAt = viewResponse?.created_at

def threadText = "Elixir Ticket: " + (viewResponse?.status ?: "") + "\n";
if (viewResponse?.message) {
    threadText += viewResponse.message
}


def threads = [[
                       text           : threadText,
                       contentType    : 'text/plain',
                       threadEntryType: [id: 30, name: null],
                       createdByUser  : "FF",
                       action         : 'add'
               ]]

return [
        threads   : threads,
        elxrTicket: base
]
