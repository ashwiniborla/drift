/**
 * Extracts Elixir ticket payload from IMS GET /incidents/{id} response (_response).
 * Used as transformerScript for HTTP node get_incident.
 *
 * Returns: elxrTkt (map), elixirTicketId (string id for prepare_elixir_ticket_update).
 * Throws if elxrTkt.id is missing or blank.
 */

def elxrTkt = _response?.incidentResponseData?.customFields?.elxrTkt
def id = elxrTkt?.id?.toString()?.trim()
if (!id) {
    throw new IllegalStateException('Elixir ticket not found or accepted')
}

return [
        elxrTkt       : elxrTkt,
        elixirTicketId: id,
]
