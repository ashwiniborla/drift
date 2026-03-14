/**
 * transform_create_address.groovy
 *
 * Transforms the User Service POST /contacts response into a flat map
 * consumed by the chore_confirm_address node.
 *
 * User Service create-contact response shape:
 *   { "id": "CNTCT_NEW_..." }
 *
 * Context:
 *   _response – raw HTTP response from User Service createContact API
 *
 * Returns:
 *   [
 *     newAddressId: "CNTCT_NEW_..."
 *   ]
 */

if (_response == null) {
    throw new Exception("User Service createContact API response is null")
}

def newAddressId = _response?.id?.toString()
if (!newAddressId) {
    throw new Exception("New contact ID not returned by User Service: ${_response}")
}

return [
    newAddressId: newAddressId
]
