# check_similarity_and_create_incident

## Overview
- **ID**: `check_similarity_and_create_incident`
- **Type**: `HTTP`
- **Version**: `1`
- **Purpose**: Generic node to call checkSimilarityAndCreate API; creates an incident with the issue, queue, customer, and order details passed via node parameters. Use in any workflow that needs to create an incident.

## Parameters

| Parameter       | Description                    | Source (workflow bindings)   |
|----------------|--------------------------------|-------------------------------|
| workflow_id    | Workflow instance id (required)| e.g. `$.workflowId`           |
| issue_detail   | { issueId, issueName }         | e.g. `$.issueDetail`          |
| queue_detail   | { queueId, queueName }         | e.g. `$.queueDetail`          |
| customer       | { customerId }                | e.g. `$.customer`             |
| order_details  | [ { orderId, orderItemId, orderItemUnitId } ] | e.g. `$.orderDetails` |

## Script Details

### body (dynamic)
- **Location**: `worker/src/main/resources/scripts/generic/build_create_incident_body.groovy`
- **Context access**: `_global.nodeParameters.workflow_id`, `issue_detail`, `queue_detail`, `customer`, `order_details`
- **Logic summary**: Builds WorkflowStartRequest body from node parameters.

### transformer (in-node)
- **Logic summary**: Maps API response to `incident_id`, `duplicate_incident_found`.

## Output
- **Returns**: Map stored under the state key (e.g. `create_questionnaire_incident`):
  - `incident_id` (String — the created incident externalId)
  - `duplicate_incident_found` (Boolean)

## Dependencies
- **Reads from**: `_global.nodeParameters` only (no direct _global reads for body).
- **Workflow**: Must bind `workflow_id`, `issue_detail`, `queue_detail`, `customer`, `order_details` on the state that uses this node.
