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
 *
 * Returns: Map matching WorkflowStartRequest structure
 */

def workflowId = _global?.nodeParameters?.workflow_id ?: ""
def issueDetail = _global?.nodeParameters?.issue_detail
def queueDetail = _global?.nodeParameters?.queue_detail
def customer = _global?.nodeParameters?.customer
def orderDetails = _global?.nodeParameters?.order_details

if (workflowId == null || workflowId.toString().trim().isEmpty()) {
    throw new IllegalArgumentException("Workflow Id is required to create incident.")
}

return [
        workflowId   : workflowId,
        issueDetail  : issueDetail ?: [:],
        queueDetail  : queueDetail ?: [:],
        customer     : customer ?: [:],
        orderDetails : orderDetails ?: []
]
