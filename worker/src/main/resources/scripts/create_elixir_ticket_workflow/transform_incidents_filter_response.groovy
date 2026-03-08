/**
 * Transforms IMS incidents/filter API response for create_elixir_ticket_workflow.
 * - Filters incidents where statusResponse.statusType is in enum list (UNRESOLVED, WAITING).
 * - For each, checks customFields.elixirTicketDetails (null-safe); keeps where id != null and status in enum list (REQUESTED, ACCEPTED).
 *
 * Expects _response (API body) and _enum_store (config). In code use keys without "global." prefix (e.g. config key global.elixir.incidentFilter.statusTypes -> elixir.incidentFilter.statusTypes).
 */
def statusTypes = _enum_store?.get('elixir.incidentFilter.statusTypes') ?: []
def allowedStatuses =  _enum_store?.get('elixir.elixirTicketDetails.allowedStatuses') ?: []

def incidents = _response?.incidents ?: []
def filteredIncidents = incidents.findAll { inc ->
    def statusType = inc?.statusResponse?.statusType
    def elixirDetails = inc?.incidentResponseData?.customFields?.elixirTicketDetails
    statusType in statusTypes && elixirDetails != null && elixirDetails.id != null && elixirDetails.status in allowedStatuses
}

return [
        filteredIncidents: filteredIncidents,
        count            : filteredIncidents.size()
]
