/**
 * check_minions_eligibility.groovy
 *
 * Reads the Oxford order response and filters units that are eligible
 * for POST_DISPATCH_CHANGE_ADDRESS based on the Minions unitChangeActions.
 *
 * Context:
 *   _global.fetch_order_oxford.units        – raw units map from Oxford (keyed by unitId)
 *   _global.fetch_order_oxford.targetUnitIds – list of orderItemUnitIds from workflow input
 *
 * Returns:
 *   [
 *     eligibleUnitIds  : [ list of unit IDs eligible for post-dispatch address change ],
 *     deliveryAddressId: "CNTCT..." (from first eligible unit's toParty),
 *     isEligible       : true | false
 *   ]
 */

def fetchResult = _global?.fetch_order_oxford
if (!fetchResult) {
    throw new Exception("fetch_order_oxford output not found in workflow context")
}

def allUnits = fetchResult.units ?: [:]
def targetUnitIds = fetchResult.targetUnitIds ?: []

if (targetUnitIds.isEmpty()) {
    throw new Exception("No target unit IDs found in workflow context")
}

def eligibleUnitIds = []
def deliveryAddressId = null

targetUnitIds.each { unitId ->
    def unit = allUnits[unitId.toString()]
    if (!unit) return

    def changeActions = unit?.unitChangeActions ?: []
    def postDispatchAction = changeActions.find {
        it?.actionType?.toString() == 'CHANGE_SECONDARY_PHONE_NUMBER'
    }

    if (postDispatchAction?.eligibility == true) {
        eligibleUnitIds << unitId.toString()
        // All units share the same delivery address; capture it from the first eligible unit
        if (!deliveryAddressId) {
            deliveryAddressId = unit?.toParty?.deliveryAddressId?.toString()
        }
    }
}

return [
    eligibleUnitIds  : eligibleUnitIds,
    deliveryAddressId: deliveryAddressId,
    isEligible       : !eligibleUnitIds.isEmpty()
]
