/**
 * Extracts postFulfillmentData for the first unit matching a specific tracking ID from Oxford resolved variables response.
 * Used by elixir_get_order_details node transformer. Expects the API response as a Map (_response), not a JSON string.
 *
 * @param response The Oxford API response (Map, i.e. _response)
 * @param orderId The target Order ID
 * @param dataVariable The variable name containing the order value data (e.g. v2OrderData_imsv2_varadhi_client1_default)
 * @param targetTrackingId The tracking ID to search for
 * @return Map with itemType and postFulfillmentData
 * @throws Exception if response is null, units not found, no matching unit, or itemType is null
 */

def getPostFulfillmentDataByTrackingId(response, String orderId, String dataVariable, String targetTrackingId) {
    if (response == null) {
        throw new Exception("Oxford API response is null")
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

    def matchedUnit = unitsMap.values().find { unit ->
        unit?.postFulfillmentData?.trackingId == targetTrackingId
    }

    if (!matchedUnit?.postFulfillmentData) {
        throw new Exception("No unit found with tracking ID: ${targetTrackingId}")
    }

    def partnerList = _enum_store?.postDeliveryIssues?.eklPartners ?: []

    def allowed = partnerList.contains(matchedUnit.postFulfillmentData.courierName);

    def result = [
            itemType           : matchedUnit?.type?.toUpperCase(),
            postFulfillmentData: matchedUnit.postFulfillmentData,
            allowed: allowed,
    ]

    if (result.itemType == null) {
        throw new Exception("itemType is null for trackingId: ${targetTrackingId}")
    }

    return result
}


// Read from _global (array keys not supported in node parameters)
def firstOrder = _global?.orderDetails?.getAt(0)
def orderId = firstOrder?.orderId?.toString()
def trackingId = firstOrder?.trackingId?.toString()
def dataVar = (_global?.nodeParameters?.dataVariable ?: 'v2OrderData_imsv2_varadhi_client1_default').toString()

return getPostFulfillmentDataByTrackingId(_response, orderId, dataVar, trackingId)
