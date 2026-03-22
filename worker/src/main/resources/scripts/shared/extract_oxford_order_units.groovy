/**
 * extract_oxford_order_units.groovy
 *
 * GROOVY node script for extract_oxford_order_units.
 * Reads the raw Oxford resolved-variables response from _global.fetch_order_oxford
 * (written by the preceding e2e_fetch_order_details HTTP state with contextOverrideKey: fetch_order_oxford)
 * and returns the full units map plus the list of targetUnitIds from the workflow input.
 *
 * Context:
 *   _global.fetch_order_oxford       – raw Oxford API response (Map)
 *   _global.orderDetails             – array of {orderId, orderItemId, orderItemUnitId, trackingId}
 *   _global.nodeParameters.dataVariable – Oxford data-variable key
 *                                         (default: v2OrderData_imsv2_varadhi_client1_default)
 *
 * Returns (stored at _global.fetch_order_oxford via contextOverrideKey):
 *   [
 *     units        : { <unitId>: { status, statusHistories, chores, postFulfillmentData, promiseDataBag, ... } },
 *     targetUnitIds: [ list of orderItemUnitIds from the workflow input ]
 *   ]
 */

def rawResponse = _global?.fetch_order_oxford

if (rawResponse == null) {
    throw new Exception("fetch_order_oxford not found in workflow context — ensure e2e_fetch_order_details ran with contextOverrideKey: fetch_order_oxford")
}

def orderId = _global?.orderDetails?.getAt(0)?.orderId?.toString()
if (!orderId) {
    throw new Exception("orderId not found in _global.orderDetails")
}

def dataVar = (_global?.nodeParameters?.dataVariable
        ?: 'v2OrderData_imsv2_varadhi_client1_default').toString()

def unitsMap = rawResponse?.resolvedVariablesResponse
        ?.ORDER
        ?.pivotIdContextMap?."${orderId}"
        ?.resolvedVariables?."${dataVar}"
        ?.value
        ?.oms3_aggregated_order
        ?.oms3_order_data
        ?.units

if (!unitsMap) {
    throw new Exception("Could not locate 'units' in Oxford response for orderId: ${orderId}, dataVariable: ${dataVar}")
}

def targetUnitIds = (_global?.orderDetails ?: []).collect { it?.orderItemUnitId?.toString() }.findAll { it }

return [
        units        : unitsMap,
        targetUnitIds: targetUnitIds
]
