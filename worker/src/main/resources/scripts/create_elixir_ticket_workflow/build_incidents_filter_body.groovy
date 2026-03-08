/**
 * Builds request body for IMS incidents/filter API (create_elixir_ticket_workflow).
 * Reads orderId from _global.orderDetails[0], issueIds from enum store key
 * Config key in lookup: global.elixir.issueConfig.<issue_id>.similarIssues. In code use without "global.": elixir.issueConfig.<issue_id>.similarIssues (comma-separated -> array).
 * Throws if orderId or issueIds are missing.
 */
def orderId = _global?.orderDetails?.getAt(0)?.orderId?.toString()?.trim()
if (!orderId) {
    throw new Exception('orderId is required for incidents filter: missing or empty from orderDetails[0]')
}

def issueIdParam = _global?.params?.issueId
if (issueIdParam == null || issueIdParam.toString().trim().isEmpty()) {
    throw new Exception('issueId is required for incidents filter: missing from params or issueDetail')
}

def similarIssuesKey = 'elixir.issueConfig.' + issueIdParam + '.similarIssues'
def issueIds = _enum_store?.get(similarIssuesKey) ?: []
if (issueIds == null) {
    throw new Exception("issueIds for filter not found in enum store for key: ${similarIssuesKey}")
}

if (issueIds.isEmpty()) {
    throw new Exception("issueIds for filter are empty after parsing enum store key: ${similarIssuesKey}")
}

return [
    orderDetailFilter: [orderIds: [orderId]],
    issueIds         : issueIds
]
