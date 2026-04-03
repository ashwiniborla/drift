/**
 * Builds dynamic inputOptions for fake_workflow_show_instructions from reporting Oxford data
 * in _global.fetch_order_oxford (e2e_fetch_order_details + contextOverrideKey fetch_order_oxford).
 *
 * Expects:
 *   _global.fetch_order_oxford
 *   _global.orderDetails[0].orderId
 *   _global.orderDetails[].orderItemUnitId (preferred unit selection order)
 *   _global.nodeParameters.dataVariable (e.g. v2OrderData_imsv2_varadhi_client1_reporting)
 *
 * Returns: List of option maps (static_text block + Done button) for possibleDynamicValues.
 */

def raw = _global?.fetch_order_oxford
if (raw == null) {
    throw new Exception('fetch_order_oxford missing — run e2e_fetch_order_details with contextOverrideKey fetch_order_oxford')
}

def orderId = _global?.orderDetails?.getAt(0)?.orderId?.toString()
if (!orderId) {
    throw new Exception('orderId missing in _global.orderDetails')
}

def dataVar = (_global?.nodeParameters?.dataVariable ?: 'v2OrderData_imsv2_varadhi_client1_reporting').toString()

def pivotCtx = raw?.resolvedVariablesResponse?.ORDER?.pivotIdContextMap?."${orderId}"
def orderValue = pivotCtx?.resolvedVariables?."${dataVar}"?.value
if (!orderValue) {
    throw new Exception("Could not read Oxford order value for orderId=${orderId}, dataVariable=${dataVar}")
}

def unitsMap = orderValue?.oms3_aggregated_order?.oms3_order_data?.units
if (!unitsMap) {
    throw new Exception("units map missing in reporting Oxford payload for orderId=${orderId}")
}

def entityViews = orderValue?.zulu_data?.entityViews ?: []

def zone = java.time.ZoneId.of('Asia/Kolkata')
def dtf = java.time.format.DateTimeFormatter.ofPattern('dd MMM yy, hh:mm a', java.util.Locale.ENGLISH)

def formatMs = { ms ->
    if (ms == null) {
        return ''
    }
    long l
    if (ms instanceof Number) {
        l = ms.longValue()
    } else {
        try {
            l = Long.parseLong(ms.toString())
        } catch (ignored) {
            return ''
        }
    }
    if (l <= 0) {
        return ''
    }
    return java.time.Instant.ofEpochMilli(l).atZone(zone).format(dtf)
}

def imageForUnit = { unit ->
    def listingId = unit?.productData?.listingId?.toString()
    if (!listingId) {
        return ''
    }
    def match = entityViews.find { it?.entityId?.toString() == listingId }
    return match?.view?.product?.image_url?.toString() ?: ''
}

def targetIds = (_global?.orderDetails ?: []).collect { it?.orderItemUnitId?.toString() }.findAll { it }

def productDetails = []
def seenListing = new LinkedHashSet()

def addUnit = { u ->
    if (!u?.productData) {
        return
    }
    def lid = u.productData?.listingId?.toString() ?: u.id?.toString()
    if (lid && seenListing.contains(lid)) {
        return
    }
    if (lid) {
        seenListing.add(lid)
    }
    productDetails << [
            productImageLink: imageForUnit(u) ?: '',
            productTitle      : (u.productData?.title ?: 'Product').toString()
    ]
}

for (uid in targetIds) {
    def u = unitsMap[uid]
    if (u && u.flow?.toString() != 'RETURN') {
        addUnit(u)
    }
}

if (productDetails.isEmpty()) {
    unitsMap.each { _, u ->
        if (u?.flow?.toString() == 'FORWARD' && u?.productData) {
            addUnit(u)
        }
    }
}

if (productDetails.isEmpty()) {
    productDetails << [productImageLink: '', productTitle: 'Product details unavailable']
}

def primaryUnit = null
if (targetIds) {
    primaryUnit = targetIds.collect { unitsMap[it] }.find { it != null }
}
if (!primaryUnit) {
    primaryUnit = unitsMap.values().find { it?.flow?.toString() == 'FORWARD' }
}
if (!primaryUnit) {
    primaryUnit = unitsMap.values().find { it != null }
}

def pb = primaryUnit?.promiseDataBag
def expectedShipping = formatMs(pb?.promisedDateFrom)
def promiseRaw = (pb?.updatedPromisedDateTo instanceof Number && (pb.updatedPromisedDateTo as long) > 0)
        ? pb.updatedPromisedDateTo
        : pb?.promisedDateTo
def promiseDelivery = formatMs(promiseRaw)
def statusText = (primaryUnit?.status ?: primaryUnit?.rolledUpStatus ?: 'unknown').toString().toLowerCase()

def instructionsBody = '''Inform customer :

 Step 1: Delivery date and time from SA (11 pm/grocery slot/large slot).
 Step 2: Shipping date.
 Step 3: OFD(Out for Delivery) day info and customer can contact Delivery Executive (DE)

 a.Ekart: DE's number will be available on app.
 b.Other delivery partner(3PL): SMS will be sent with DE's detail.

 Step 4: Pick up call from the delivery executive on OFD day (number may reflect as SPAM).

 Educate:Easy to track order via 'Orders' section and share steps if needed (Shipped/OFD)'''

def staticBlock = [
        id         : 'fake_delivery_attempt',
        description: 'Show static text',
        tags       : [values: ['sa.static_text']],
        instructions: [
                [
                        templateId        : 'text_to_display',
                        templateVariables : [
                                title        : 'Message to customer',
                                messageToShow: 'Inform instructions to the customer'
                        ]
                ],
                [
                        templateId        : 'order_items',
                        templateVariables : [
                                productDetails       : productDetails,
                                expected_shipping_date: expectedShipping,
                                promise_delivery_date : promiseDelivery,
                                status                : statusText
                        ]
                ],
                [
                        templateId        : 'text_to_display',
                        templateVariables : [
                                title        : 'Instructions',
                                messageToShow: instructionsBody
                        ]
                ]
        ]
]

def buttonBlock = [
        id          : 'fake_delivery_attempt_proceed_button_widget',
        description : 'Select a button',
        tags        : [values: ['sa.button_widget']],
        possibleValues: [
                [displayValue: 'Done', value: 'DONE']
        ]
]

return [staticBlock, buttonBlock]
