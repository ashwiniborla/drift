/**
 * build_create_incident_body.groovy
 *
 * Builds a WorkflowStartRequest body for createIncidentV3 API (checkSimilarityAndCreate).
 *
 * Context (via nodeParameters; workflow must pass these on the node state):
 *   _global.nodeParameters.workflow_id   – workflow instance id (required)
 *   _global.nodeParameters.issue_detail – { issueId, issueName }
 *   _global.nodeParameters.queue_detail – { queueId, queueName }
 *   _global.nodeParameters.customer     – { customerId }
 *   _global.nodeParameters.order_details – [ { orderId, orderItemId, orderItemUnitId } ]
 *   _global.nodeParameters.override_issue_id – optional; when set (e.g. in subworkflow), issueDetail is replaced with { issueId: override_issue_id, issueName: null }
 *
 * Returns: Map matching WorkflowStartRequest structure
 */

def workflowId = _global?.nodeParameters?.workflow_id ?: ""
def issueDetail = _global?.nodeParameters?.issue_detail
def queueDetail = _global?.nodeParameters?.queue_detail
def customer = _global?.nodeParameters?.customer
def orderDetails = _global?.nodeParameters?.order_details
def overrideIssueId = _global?.nodeParameters?.override_issue_id

if (workflowId == null || workflowId.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("Workflow Id is required to create incident.")
}

// When override_issue_id is present (e.g. subworkflow: from process_fake_workflow_details.issueConfig.fakeIssueId), use it for issueId and set issueName to null.
// When absent (e.g. direct workflow), this block is skipped and issueDetail from context is used — no break.
if (overrideIssueId != null && overrideIssueId.toString().trim().length() > 0) {
    issueDetail = [issueId: overrideIssueId.toString(), issueName: null]
}

return [
        workflowId   : workflowId,
        issueDetail  : issueDetail ?: [:],
        queueDetail  : queueDetail ?: [:],
        customer     : customer ?: [:],
        orderDetails : orderDetails ?: []
]
