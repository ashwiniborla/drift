/**
 * build_chore_feasibility_body.groovy
 *
 * Builds the request body for:
 *   POST /api/v3/order/changeAddressFeasibility/v2
 *
 * For ADD_ALTERNATE_CONTACT (phone-only change), newPincode == oldPincode
 * because the physical address is not changing.
 *
 * Node parameters:
 *   fieldsUpdatedV2 (required) — forwarded in the JSON body as fieldsUpdatedV2.
 *   Documented expected values (not validated in script): PINCODE, TEXT,
 *   TEXT_AND_PHONE_NUMBER, PHONE_NUMBER, SECONDARY_PHONE_NUMBER.
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
def fieldsParam       = _global?.nodeParameters?.fieldsUpdatedV2
def fieldsUpdatedV2   = (fieldsParam != null && fieldsParam.toString().trim()) ? fieldsParam.toString().trim() : null

if (!orderId) throw new Exception("orderId not found in workflow context for chore_feasibility")
if (!choreId) throw new Exception("choreId not found from chore_eligibility output")
if (!pincode) throw new Exception("pincode not found from get_current_address output")
if (!fieldsUpdatedV2) throw new Exception("fieldsUpdatedV2 node parameter is required for changeAddressFeasibility/v2")

return [
    choreId          : choreId,
    orderVersion     : -1,
    orderId          : orderId,
    orderItemUnitIds : actionableUnitIds,
    newPincode       : pincode,
    oldPincode       : pincode,
    fieldsUpdatedV2  : fieldsUpdatedV2
]
