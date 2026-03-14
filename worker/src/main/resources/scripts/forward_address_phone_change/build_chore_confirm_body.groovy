/**
 * build_chore_confirm_body.groovy
 *
 * Builds the request body for:
 *   POST /api/v3/order/changeAddressConfirm/v2
 *
 * For ADD_ALTERNATE_CONTACT (phone-only change):
 *   - newPincode == oldPincode (physical address unchanged)
 *   - addressId = newly created contact from User Service
 *   - fieldsUpdated = "TEXT_AND_PHONE_NUMBER"  (covers alt phone update)
 *   - shouldUnhold = false
 *
 * Context:
 *   _global.orderDetails[0].orderId               – order ID
 *   _global.chore_eligibility.choreId             – choreId from eligibility step
 *   _global.chore_feasibility.actionableUnitIds   – eligible unit IDs
 *   _global.get_current_address.address.pincode.input  – pincode (same old/new)
 *   _global.create_new_address.newAddressId       – newly created contact ID
 */

def orderId          = _global?.orderDetails?.getAt(0)?.orderId?.toString()
def choreId          = _global?.chore_eligibility?.choreId?.toString()
def unitIds          = (_global?.chore_feasibility?.actionableUnitIds ?: [])
def pincode          = _global?.get_current_address?.address?.pincode?.input?.toString()
def newAddressId     = _global?.create_new_address?.newAddressId?.toString()

if (!orderId)      throw new Exception("orderId not found for chore_confirm_address")
if (!choreId)      throw new Exception("choreId not found from chore_eligibility output")
if (!pincode)      throw new Exception("pincode not found from get_current_address output")
if (!newAddressId) throw new Exception("newAddressId not found from create_new_address output")

return [
    choreId          : choreId,
    orderVersion     : -1,
    orderId          : orderId,
    orderItemUnitIds : unitIds,
    newPincode       : pincode,
    oldPincode       : pincode,
    addressId        : newAddressId,
    userLogin        : 'fk_selfServe',
    shouldUnhold     : false,
    fieldsUpdated    : 'TEXT_AND_PHONE_NUMBER'
]
