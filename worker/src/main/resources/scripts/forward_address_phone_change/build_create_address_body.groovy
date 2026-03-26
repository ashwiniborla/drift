/**
 * build_create_address_body.groovy
 *
 * Builds the request body for:
 *   POST /contacts/{customerId}
 *
 * This creates a new contact in User Service by copying the existing
 * physical address details. The primary phone is preserved from the
 * existing address; only the alternate phone is updated, sourced from
 * the smart-action viewResponse collected via ask_alternate_phone_smart
 * (stored under the contextOverrideKey "ask_alternate_phone").
 *
 * Context:
 *   _global.get_current_address                                     – raw User Service contact response
 *   _global['ask_alternate_phone:viewResponse'].reciever_alternate_phone_number  – alternate phone from customer
 */

def current  = _global?.get_current_address ?: [:]
def addr     = current?.address ?: [:]

def primaryPhone = ''
def existingComms = current?.communications
if (existingComms instanceof List) {
    def mobileComm = existingComms.find { it?.type == 'mobile' }
    primaryPhone = mobileComm?.input?.toString() ?: ''
}

if (!primaryPhone) {
    throw new Exception("Primary phone not found in existing address (get_current_address.communications)")
}

def altPhone = _global['ask_alternate_phone:viewResponse']?.selectedOptions?.reciever_alternate_phone_number?.toString()?.trim()
if (!altPhone) {
    throw new Exception("Alternate phone number is required but was not provided")
}

def communications = [[type: 'mobile', input: primaryPhone], [type: 'alt_phone', input: altPhone]]

def body = [
        userName: [input: current?.name?.input ?: ''],
        address: [
                addressType  : 'user_generated',
                addressLine1 : addr?.addressLine1?.input ?: '',
                addressLine2 : addr?.addressLine2?.input ?: '',
                city         : addr?.city?.input ?: '',
                state        : addr?.state?.input ?: '',
                country      : addr?.country?.input ?: '',
                pincode      : addr?.pincode?.input ?: '',
                landmark     : addr?.landmark?.input ?: ''
        ],
        communications: communications,
        accountId: current?.accountId,
        creatingChannel: 'flipkart'
]

if (addr?.locationTypeTag) {
    body.address.locationTypeTag = addr.locationTypeTag
}

return body
