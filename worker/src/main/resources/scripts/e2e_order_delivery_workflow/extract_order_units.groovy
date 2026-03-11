/**
 * extract_order_units.groovy
 *
 * Oxford response transformer for the e2e_fetch_order_details node.
 * Extracts the full units map from the Oxford resolved-variables response so
 * that downstream use-case scripts can evaluate order-unit statuses, chores,
 * and promise data.
 *
 * Context:
 *   _response  – raw Oxford API response (Map)
 *   _global.orderDetails          – array of {orderId, orderItemId, orderItemUnitId, trackingId}
 *   _global.nodeParameters.dataVariable – Oxford data-variable key
 *                                         (default: v2OrderData_cs_controller_client_check_default)
 *
 * Returns:
 *   [
 *     units        : { <unitId>: { status, statusHistories, chores, postFulfillmentData, promiseDataBag, ... } },
 *     targetUnitIds: [ list of orderItemUnitIds from the workflow input ]
 *   ]
 */

if (_response == null) {
    throw new Exception("Oxford API response is null")
}

def orderId = _global?.orderDetails?.getAt(0)?.orderId?.toString()
if (!orderId) {
    throw new Exception("orderId not found in _global.orderDetails")
}

def dataVar = (_global?.nodeParameters?.dataVariable
        ?: 'v2OrderData_cs_controller_client_check_default').toString()

def unitsMap = _response?.resolvedVariablesResponse
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
