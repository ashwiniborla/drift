/**
 * build_create_address_body.groovy
 *
 * Builds the request body for:
 *   POST /contacts/{customerId}
 *
 * This creates a new contact in User Service by copying the existing
 * physical address details and substituting in the new phone numbers
 * collected from the agent via ask_alternate_phone.
 *
 * Context:
 *   _global.get_current_address  – raw User Service contact response
 *   _global.ask_alternate_phone.newContact  – new primary phone provided by agent
 *   _global.ask_alternate_phone.altContact  – new alternate phone (optional)
 */

def current   = _global?.get_current_address ?: [:]
def addr      = current?.address ?: [:]
def newPhone  = _global?.ask_alternate_phone?.newContact?.toString() ?: ''
def altPhone  = _global?.ask_alternate_phone?.altContact?.toString() ?: ''

if (!newPhone) {
    throw new Exception("newContact (primary phone) not provided in resume payload")
}

def communications = [[type: 'mobile', input: newPhone]]
if (altPhone) {
    communications << [type: 'alt_phone', input: altPhone]
}

def body = [
    name: [input: current?.name?.input ?: ''],
    address: [
        addressType  : 'user_generated',
        addressLine1 : [input: addr?.addressLine1?.input ?: ''],
        addressLine2 : [input: addr?.addressLine2?.input ?: ''],
        city         : [input: addr?.city?.input ?: ''],
        state        : [input: addr?.state?.input ?: ''],
        country      : [input: addr?.country?.input ?: ''],
        pincode      : [input: addr?.pincode?.input ?: ''],
        landmark     : [input: addr?.landmark?.input ?: '']
    ],
    communications: communications
]

if (addr?.locationTypeTag) {
    body.address.locationTypeTag = addr.locationTypeTag
}

return body
