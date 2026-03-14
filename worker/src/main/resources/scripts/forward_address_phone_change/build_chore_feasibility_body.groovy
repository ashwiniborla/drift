/**
 * build_chore_feasibility_body.groovy
 *
 * Builds the request body for:
 *   POST /api/v3/order/changeAddressFeasibility/v2
 *
 * For ADD_ALTERNATE_CONTACT (phone-only change), newPincode == oldPincode
 * because the physical address is not changing.
 *
 * Context:
 *   _global.orderDetails[0].orderId           – order ID
 *   _global.chore_eligibility.choreId         – choreId from eligibility response
 *   _global.chore_eligibility.actionableUnitIds – unit IDs to check feasibility for
 *   _global.get_current_address.address.pincode.input  – current delivery pincode (same for old and new)
 */

def orderId           = _global?.orderDetails?.getAt(0)?.orderId?.toString()
def choreId           = _global?.chore_eligibility?.choreId?.toString()
def actionableUnitIds = (_global?.chore_eligibility?.actionableUnitIds ?: [])
def pincode           = _global?.get_current_address?.address?.pincode?.input?.toString()

if (!orderId) throw new Exception("orderId not found in workflow context for chore_feasibility")
if (!choreId) throw new Exception("choreId not found from chore_eligibility output")
if (!pincode) throw new Exception("pincode not found from get_current_address output")

return [
    choreId          : choreId,
    orderVersion     : -1,
    orderId          : orderId,
    orderItemUnitIds : actionableUnitIds,
    newPincode       : pincode,
    oldPincode       : pincode
]
