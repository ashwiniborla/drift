/**
 * build_closed_elxr_tkt.groovy
 *
 * Builds the full elxrTkt with status=CLOSED for the incident update.
 * Deep-copies the base elxrTkt from params and sets status to CLOSED.
 *
 * Reads from _global.nodeParameters.elxrTkt (passed by API, read from incident).
 */

def base = _global?.nodeParameters?.elxrTkt
if (base == null) {
    throw new IllegalArgumentException("elxrTkt not found in params; cannot build closed update.")
}

def entity = base?.entity
def entityCopy = (entity != null && entity instanceof Map)
    ? [referenceType: entity.referenceType, referenceId: entity.referenceId, type: entity.type]
    : null

return [
    id         : base?.id,
    workflowId : base?.workflowId,
    status     : 'CLOSED',
    type       : base?.type,
    entity     : entityCopy,
    createdAt  : base?.createdAt,
    updatedAt  : new Date()
]
