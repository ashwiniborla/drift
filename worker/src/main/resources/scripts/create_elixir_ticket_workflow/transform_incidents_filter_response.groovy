/**
 * Transforms IMS incidents/filter API response for create_elixir_ticket_workflow.
 * - Filters incidents where statusResponse.statusType is in enum list (UNRESOLVED, WAITING).
 * - For each, checks customFields.elixirTicketDetails (null-safe) and trackingId match.
 * - Returns only incidentId (mapped from incident.externalId) in filteredIncidents.
 *
 * Expects _response (API body) and _enum_store (config). In code use keys without "global." prefix (e.g. config key global.elixir.incidentFilter.statusTypes -> elixir.incidentFilter.statusTypes).
 */
def statusTypes = _enum_store?.elixir?.get('elixirTicketDetails.allowedStatuses') ?: []
def allowedStatuses = _enum_store?.elixir?.get('incidentFilter.statusTypes') ?: []

def incidents = _response?.incidents ?: []
def globalTrackingId = (_global?.orderDetails?.getAt(0)?.trackingId)?.toString()?.trim()

def matchesCriteria = { inc ->
    def statusType = inc?.statusResponse?.statusType
    def elixirDetails = inc?.incidentResponseData?.customFields?.elxrTkt
    def firstOrderResponse = (inc?.orderResponseList ?: inc?.incidentResponseData?.orderResponseList ?: [])?.getAt(0)
    def incidentTrackingId = firstOrderResponse?.orderDetails?.trackingId?.toString()?.trim()
    statusType in statusTypes &&
            elixirDetails != null &&
            elixirDetails.status in allowedStatuses &&
            globalTrackingId != null &&
            incidentTrackingId == globalTrackingId
}

def matchedIncidents = incidents.findAll(matchesCriteria)

def filteredIncidents = matchedIncidents.collect { inc ->
    [incidentId: inc?.externalId]
}.findAll { it?.incidentId != null }

def firstElixirDetails = matchedIncidents?.getAt(0)?.incidentResponseData?.customFields?.elxrTkt


return [
        filteredIncidents : filteredIncidents,
        firstElixirDetails: firstElixirDetails,
        count             : filteredIncidents.size()
]
