/**
 * transform_chore_confirm.groovy
 *
 * Transforms the Chore confirm address API response.
 *
 * API: POST /api/v3/order/changeAddressConfirm/v2
 *
 * Context:
 *   _response – raw HTTP response from Chore confirm API
 *
 * Returns:
 *   [
 *     choreId   : "CH...",
 *     status    : "SUCCESS",
 *     isSuccess : true | false
 *   ]
 */

if (_response == null) {
    throw new Exception("Chore confirm address API response is null")
}

def status    = _response?.status?.toString() ?: 'UNKNOWN'
def choreId   = _response?.choreId?.toString()
def isSuccess = (status == 'SUCCESS')

if (!isSuccess) {
    throw new Exception("Chore confirm address failed with status: ${status}, choreId: ${choreId}")
}

return [
    choreId  : choreId,
    status   : status,
    isSuccess: isSuccess
]
