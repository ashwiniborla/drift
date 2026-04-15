/**
 * get_post_fulfillment_data_by_tracking_id.groovy
 *
 * GROOVY node script for elixir_slice_order_from_oxford.
 * Reads the raw Oxford resolved-variables response from _global.fetch_order_oxford
 * (written by the preceding e2e_fetch_order_details HTTP state with contextOverrideKey: fetch_order_oxford),
 * finds the unit matching orderItemUnitId from _global.orderDetails[0] (Oxford units map is keyed by unit id),
 * and returns itemType, postFulfillmentData, allowed (EKL partner check), and hub_allowed (optional hub allowlist vs Oxford shipment_data).
 *
 * For reverse flow issues (flowDirection == 'reverse' in issueConfig), the delivery unit is located by
 * orderItemUnitId, then the active reverse child unit (from childUnits) is resolved and its
 * postFulfillmentData is used instead.
 *
 * @param response The Oxford API response (Map)
 * @param orderId The target Order ID
 * @param dataVariable The variable name containing the order value data (e.g. v2OrderData_imsv2_varadhi_client1_default)
 * @param targetOrderItemUnitId The order item unit id to select (matches Oxford units map key / unit.id)
 * @param isReverse Whether the issue flow is reverse (pickup/return)
 * @return Map with itemType, postFulfillmentData, allowed, hub_allowed, and allUnitsFlat (all units merged from nested childUnits)
 * @throws Exception if response is null, units not found, no matching unit, or itemType is null
 */

/** Oxford oms3_order_data.shipment_data: map keyed by external tracking id; values include shipment_id, source_hub, destination_hub. */
def getOms3OrderDataRoot(response, String orderId, String dataVariable) {
    return response?.resolvedVariablesResponse
            ?.ORDER
            ?.pivotIdContextMap?."${orderId}"
            ?.resolvedVariables?."${dataVariable}"
            ?.value
            ?.oms3_aggregated_order
}

/**
 * Resolves the shipment row for this unit. Keys are often trackingId (external_tracking_id); shipment_id inside the row matches postFulfillmentData.shipmentId.
 */
def resolveShipmentDetail(Map shipmentData, Map postFulfillmentData) {
    if (shipmentData == null || !(shipmentData instanceof Map) || shipmentData.isEmpty() || postFulfillmentData == null) {
        return null
    }
    def sid = postFulfillmentData.shipmentId?.toString()
    def tid = postFulfillmentData.trackingId?.toString()
    if (sid && shipmentData.containsKey(sid)) {
        return shipmentData[sid]
    }
    if (tid && shipmentData.containsKey(tid)) {
        return shipmentData[tid]
    }
    if (sid) {
        def byShipmentId = shipmentData.values().find { v ->
            v instanceof Map && v.shipment_id?.toString() == sid
        }
        if (byShipmentId) {
            return byShipmentId
        }
    }
    return null
}

def normalizeStringHubList(raw) {
    if (raw == null) {
        return []
    }
    if (raw instanceof List) {
        return raw.collect { it?.toString()?.trim() }.findAll { it }
    }
    if (raw instanceof String) {
        return raw.split(',').collect { it?.trim() }.findAll { it }
    }
    return []
}

/**
 * When supportedHubs is empty or unset, hub check is effectively disabled (true).
 * Otherwise requires source_hub or destination_hub from shipment detail to match the allowlist.
 */
def computeHubAllowed(List hubAllowlist, Map shipmentDetail) {
    if (hubAllowlist == null || hubAllowlist.isEmpty()) {
        return true
    }
    if (shipmentDetail == null) {
        return false
    }
    def src = shipmentDetail.source_hub?.toString()?.trim()
    def dst = shipmentDetail.destination_hub?.toString()?.trim()
    return (src && hubAllowlist.contains(src)) || (dst && hubAllowlist.contains(dst))
}

def flattenUnitsRecursive(Map rootUnits) {
    def flat = [:]
    def visit
    visit = { unit, mapKey ->
        if (unit == null) {
            return
        }
        def uid = unit.id?.toString() ?: (mapKey != null ? mapKey.toString() : null)
        if (uid) {
            flat[uid] = unit
        }
        def children = unit.childUnits
        if (children instanceof Map) {
            children.each { k, child -> visit(child, k) }
        } else if (children instanceof List) {
            children.eachWithIndex { child, idx -> visit(child, idx.toString()) }
        }
    }
    if (rootUnits instanceof Map) {
        rootUnits.each { k, unit -> visit(unit, k) }
    }
    return flat
}

/**
 * Finds the active reverse child unit within a parent unit's childUnits.
 * Mirrors OMS3OrderDataUtils.getActiveReturnUnit logic:
 *   - flow=RETURN, unitSubType=PICKUP, status in [APPROVED, IN_PROGRESS]
 *   - OR flow=FORWARD, unitSubType in [REPLACEMENT, EXCHANGE], status in [APPROVED, IN_PROGRESS]
 *
 * JSON field mapping (verified against Oxford response):
 *   unit.childUnits  → child unit map
 *   child.flow       → RETURN or FORWARD
 *   child.unitSubType → PICKUP, REPLACEMENT, EXCHANGE
 *   child.status     → APPROVED, IN_PROGRESS, etc.
 */
def getActiveReturnUnit(unit) {
    def pickupStatuses = ['APPROVED', 'IN_PROGRESS']
    def childUnits = unit?.childUnits ?: [:]

    return childUnits.values().find { child ->
        def flow    = child?.flow?.toString()?.toUpperCase()
        def subType = child?.unitSubType?.toString()?.toUpperCase()
        def status  = child?.status?.toString()?.toUpperCase()

        (flow == 'RETURN' && subType == 'PICKUP' && pickupStatuses.contains(status)) ||
        (flow == 'FORWARD' && subType in ['REPLACEMENT', 'EXCHANGE'] && pickupStatuses.contains(status))
    }
}

def getPostFulfillmentDataByOrderItemUnitId(response, String orderId, String dataVariable, String targetOrderItemUnitId, boolean isReverse) {
    if (response == null) {
        throw new Exception("Oxford API response is null")
    }

    if (!targetOrderItemUnitId?.trim()) {
        throw new Exception("targetOrderItemUnitId is null or empty")
    }

    def unitsMap = response?.resolvedVariablesResponse
            ?.ORDER
            ?.pivotIdContextMap?."${orderId}"
            ?.resolvedVariables?."${dataVariable}"
            ?.value
            ?.oms3_aggregated_order
            ?.oms3_order_data
            ?.units

    if (!unitsMap) {
        throw new Exception("Could not locate 'units' in the JSON structure for Order ID: ${orderId}")
    }

    def allUnitsFlat = flattenUnitsRecursive(unitsMap)

    def matchedUnit = allUnitsFlat[targetOrderItemUnitId]
    if (!matchedUnit) {
        matchedUnit = allUnitsFlat.values().find { unit ->
            unit?.id?.toString() == targetOrderItemUnitId
        }
    }

    if (!matchedUnit?.postFulfillmentData) {
        throw new Exception("No unit found with orderItemUnitId: ${targetOrderItemUnitId}")
    }

    def effectiveUnit = matchedUnit

    if (isReverse) {
        def reverseUnit = getActiveReturnUnit(matchedUnit)
        if (!reverseUnit?.postFulfillmentData) {
            throw new Exception("No active reverse unit found under delivery unit with orderItemUnitId: ${targetOrderItemUnitId}")
        }
        effectiveUnit = reverseUnit
    }

    def oms3Root = getOms3OrderDataRoot(response, orderId, dataVariable)
    def shipmentData = oms3Root?.shipment_data ?: oms3Root?.shipmentData
    def supportedHubsRaw = _enum_store?.elixir?.supportedHubs
    def hubAllowlist = normalizeStringHubList(supportedHubsRaw)
    def shipmentDetail = resolveShipmentDetail(shipmentData instanceof Map ? shipmentData : null, effectiveUnit.postFulfillmentData)
    def hub_allowed = computeHubAllowed(hubAllowlist, shipmentDetail)

    def partnerList = _enum_store?.get("postDeliveryIssues.eklPartners") ?: []

    def allowed = partnerList.contains(effectiveUnit.postFulfillmentData.courierName)

    def result = [
            itemType           : effectiveUnit?.type?.toUpperCase(),
            postFulfillmentData: effectiveUnit.postFulfillmentData,
            allowed            : allowed,
            hub_allowed        : hub_allowed,
            allUnitsFlat       : allUnitsFlat,
    ]

    if (result.itemType == null) {
        throw new Exception("itemType is null for orderItemUnitId: ${targetOrderItemUnitId}")
    }

    return result
}


// Read from _global.fetch_order_oxford (raw Oxford response placed there by e2e_fetch_order_details HTTP state)
def rawResponse = _global?.fetch_order_oxford
if (rawResponse == null) {
    throw new Exception("fetch_order_oxford not found in workflow context — ensure e2e_fetch_order_details ran with contextOverrideKey: fetch_order_oxford")
}

def firstOrder = _global?.orderDetails?.getAt(0)
def orderId = firstOrder?.orderId?.toString()
def orderItemUnitId = firstOrder?.orderItemUnitId?.toString()
def dataVar = (_global?.nodeParameters?.dataVariable ?: 'v2OrderData_imsv2_varadhi_client1_default').toString()

// Determine flow direction from issueConfig (same approach as prepare_create_ticket_details.groovy)
def issueId = _global?.params?.issueId?.toString()?.trim()
def issueConfig = _enum_store?.elixir?.issueConfig?.get(issueId)
def entityFlow = issueConfig?.flowDirection?.toString()?.trim()?.toLowerCase()
def isReverse = entityFlow == 'reverse'

return getPostFulfillmentDataByOrderItemUnitId(rawResponse, orderId, dataVar, orderItemUnitId, isReverse)
