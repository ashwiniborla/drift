import groovy.json.JsonSlurper

/**
 * Extracts postFulfillmentData for the first unit matching a specific tracking ID.
 *
 * @param jsonPayload The raw JSON string
 * @param orderId The target Order ID
 * @param dataVariable The variable name containing the order value data
 * @param targetTrackingId The tracking ID to search for
 * @return The postFulfillmentData map, or null if not found
 */
def getPostFulfillmentDataByTrackingId(String jsonPayload, String orderId, String dataVariable, String targetTrackingId) {

    // 1. Parse the JSON string
    def parsedJson
    try {
        parsedJson = new JsonSlurper().parseText(jsonPayload)
    } catch (Exception e) {
        println "Failed to parse JSON: ${e.message}"
        return null
    }

    // 2. Navigate the deep JSON structure using the Safe Navigation Operator (?.)
    // This ensures that if any intermediate node is null, it won't throw an error, it will just return null.
    def unitsMap = parsedJson?.resolvedVariablesResponse
            ?.ORDER
            ?.pivotIdContextMap?."${orderId}"
            ?.resolvedVariables?."${dataVariable}"
            ?.value
            ?.oms3_aggregated_order
            ?.oms3_order_data
            ?.units

    // 3. Early exit if the units map doesn't exist
    if (!unitsMap) {
        println "Could not locate 'units' in the JSON structure for Order ID: ${orderId}"
        return null
    }

    // 4. Find the first unit that matches the target trackingId
    // .values() iterates over the unit objects, ignoring the parent keys
    def matchedUnit = unitsMap.values().find { unit ->
        unit?.postFulfillmentData?.trackingId == targetTrackingId
    }

    // 5. Return the postFulfillmentData if a match was found
    if (matchedUnit?.postFulfillmentData) {

        return [
                itemType           : matchedUnit?.type,
                postFulfillmentData: matchedUnit.postFulfillmentData
        ]
    } else {
        println "No unit found with tracking ID: ${targetTrackingId}"
        return null
    }
}

// ==========================================
// Usage Example
// ==========================================

// Assuming 'jsonString' holds the content of your uploaded file
String jsonString = new File('/Users/manav.chawla/oxfordResponse.json').text

// Define your variables as requested
String targetOrderId = "OD336920613620124200"
String targetVariable = "v2OrderData_imsv2_varadhi_client1_default"
String searchTrackingId = "FMPC5831452742" // Replace with "FMPC5831452742" to test against the actual JSON data provided

// Execute the function
def result = getPostFulfillmentDataByTrackingId(jsonString, targetOrderId, targetVariable, searchTrackingId)

if (result) {
    println "Successfully extracted postFulfillmentData:"
    println result
}