/**
 * get_post_fulfillment_data_by_tracking_id.groovy
 *
 * GROOVY node script for elixir_slice_order_from_oxford.
 * Reads the raw Oxford resolved-variables response from _global.fetch_order_oxford
 * (written by the preceding e2e_fetch_order_details HTTP state with contextOverrideKey: fetch_order_oxford),
 * finds the unit matching the trackingId from _global.orderDetails[0], and returns
 * itemType, postFulfillmentData, and the allowed flag (EKL partner check).
 *
 * @param response The Oxford API response (Map)
 * @param orderId The target Order ID
 * @param dataVariable The variable name containing the order value data (e.g. v2OrderData_imsv2_varadhi_client1_default)
 * @param targetTrackingId The tracking ID to search for
 * @return Map with itemType, postFulfillmentData, and allowed
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

    def partnerList = _enum_store?.get("postDeliveryIssues.eklPartners") ?: []

    def allowed = partnerList.contains(matchedUnit.postFulfillmentData.courierName)

    def result = [
            itemType           : matchedUnit?.type?.toUpperCase(),
            postFulfillmentData: matchedUnit.postFulfillmentData,
            allowed            : allowed,
    ]

    if (result.itemType == null) {
        throw new Exception("itemType is null for trackingId: ${targetTrackingId}")
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
def trackingId = firstOrder?.trackingId?.toString()
def dataVar = (_global?.nodeParameters?.dataVariable ?: 'v2OrderData_imsv2_varadhi_client1_default').toString()

return getPostFulfillmentDataByTrackingId(rawResponse, orderId, dataVar, trackingId)
