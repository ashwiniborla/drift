/**
 * Builds full elxrTkt for the second incident update (after callback).
 * Backend treats customFields.elxrTkt as a single object: sending only some fields replaces the entire
 * object and drops entity/workflowId/type etc. So we send the full object: initial elxrTkt from
 * elixir_create_ticket (entity, workflowId, type, createdAt) with id, status, updatedAt from callback.
 */
def base = _global?.elixir_create_ticket?.elxrTkt
if (base == null) {
    throw new IllegalArgumentException("elixir_create_ticket.elxrTkt not found in context; cannot build full elxrTkt update.")
}

def viewResponse = _global.get('elixir_waiting_for_response:viewResponse')?.selectedOptions

// Deep-copy entity so we return a full, self-contained object
def entity = base?.entity
def entityCopy = (entity != null && entity instanceof Map)
    ? [referenceType: entity.referenceType, referenceId: entity.referenceId, type: entity.type]
    : null

return [
    id         : viewResponse?.ticket_id,
    workflowId : base?.workflowId,
    status     : viewResponse?.status,
    type       : base?.type,
    entity     : entityCopy,
    createdAt  : base?.createdAt,
    updatedAt  : viewResponse?.created_at
]
