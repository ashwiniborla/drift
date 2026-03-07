/**
 * build_create_incident_body.groovy
 *
 * Builds a WorkflowStartRequest body for createIncidentV3 API.
 *
 * Context:
 *   _global.issueDetail   – { issueId, issueName }
 *   _global.workflowId   – { workflowId }
 *   _global.queueDetail   – { queueId, queueName }
 *   _global.customer       – { customerId }
 *   _global.orderDetails   – [ { orderId, orderItemId, orderItemUnitId } ]
 *
 * Returns: Map matching WorkflowStartRequest structure
 */

def issueDetail = _global?.issueDetail
def queueDetail = _global?.queueDetail
def customer = _global?.customer
def orderDetails = _global?.orderDetails
def workflowId = _global?.workflowId ?: ""

if(workflowId.isEmpty()) {
    throw new IllegalArgumentException("Workflow Id is required to create incident.")
}


return [
        workflowId: workflowId ?: [:],
        issueDetail : issueDetail ?: [:],
        queueDetail : queueDetail ?: [:],
        customer    : customer ?: [:],
        orderDetails: orderDetails ?: []
]
