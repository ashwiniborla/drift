/**
 * build_evaluate_location_http_body.groovy
 *
 * Full POST body for AIS POST /api/v3/contact/location/evaluate (evaluate_location HTTP node).
 * accountId (ACC...), contactId (CNTCT... from unit.toParty.deliveryAddressId), context.
 *
 * Context:
 *   _global.fetch_order_oxford   – units + targetUnitIds (after extract_oxford_order_units)
 *   _global.customer             – accountId preferred; customerId fallback
 *   _global.get_current_address  – fallback contactId if Oxford has no toParty.deliveryAddressId
 */

def fetchResult = _global?.oxford_unit_details
if (!fetchResult) {
    throw new Exception("fetch_order_oxford output not found in workflow context")
}

def allUnits = fetchResult.units ?: [:]
def targetUnitIds = fetchResult.targetUnitIds ?: []

if (targetUnitIds.isEmpty()) {
    throw new Exception("No target unit IDs found in workflow context")
}

def deliveryContactId = null
def accountIdFromOrder = null

targetUnitIds.each { unitId ->
    if (deliveryContactId) {
        return
    }
    def unit = allUnits[unitId.toString()]
    if (!unit) {
        return
    }
    def id = unit?.toParty?.deliveryAddressId?.toString()
    if (id) {
        deliveryContactId = id
        accountIdFromOrder = unit?.toParty?.accountId?.toString()
    }
}

def contactId = (deliveryContactId ?: _global?.get_current_address?.id)?.toString()
if (!contactId) {
    throw new Exception("contactId could not be resolved (Oxford toParty.deliveryAddressId or get_current_address.id)")
}

def accountId = (_global?.customer?.accountId ?: accountIdFromOrder ?: _global?.customer?.customerId)?.toString()
if (!accountId) {
    throw new Exception("accountId could not be resolved")
}

return [
        accountId: accountId,
        contactId: contactId,
        context  : [
                marketplace: 'FLIPKART',
                flowType   : 'POST_ORDER'
        ]
]
