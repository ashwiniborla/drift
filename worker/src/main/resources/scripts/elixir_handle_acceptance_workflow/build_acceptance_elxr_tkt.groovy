/**
 * build_acceptance_elxr_tkt.groovy
 *
 * Builds the full elxrTkt for the incident update after Elixir acceptance callback.
 * Backend treats customFields.elxrTkt as a single object: sending only some fields
 * replaces the entire object. So we merge the base elxrTkt (from params, originally
 * read from the incident by the API) with the callback fields.
 *
 * Reads from _global.nodeParameters (set by the API that spawned this workflow):
 *   - elxrTkt    : base object with workflowId, type, entity, createdAt
 *   - ticketId   : Elixir ticket ID from callback
 *   - status     : CREATED or REJECTED
 *   - createdAt  : timestamp from callback (when Elixir created the ticket)
 */

def base = _global?.nodeParameters?.elxrTkt
if (base == null) {
    throw new IllegalArgumentException("elxrTkt not found in params; cannot build acceptance update.")
}

def entity = base?.entity
def entityCopy = (entity != null && entity instanceof Map)
    ? [referenceType: entity.referenceType, referenceId: entity.referenceId, type: entity.type]
    : null

return [
    id         : _global?.nodeParameters?.ticketId,
    workflowId : base?.workflowId,
    status     : _global?.nodeParameters?.status,
    type       : base?.type,
    entity     : entityCopy,
    createdAt  : base?.createdAt,
    updatedAt  : _global?.nodeParameters?.createdAt
]
