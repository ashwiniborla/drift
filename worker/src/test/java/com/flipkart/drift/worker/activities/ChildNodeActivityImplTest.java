package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.node.ChildNode;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.worker.Utility.NodeParameterEvaluator;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;
import static org.junit.jupiter.api.Assertions.*;

class ChildNodeActivityImplTest {

    private WorkflowContextHBService workflowContextHBService;
    private ChildNodeActivityImpl childNodeActivity;

    @BeforeEach
    void setUp() {
        workflowContextHBService = Mockito.mock(WorkflowContextHBService.class);
        childNodeActivity = new ChildNodeActivityImpl(workflowContextHBService);
    }

    // ========================== executeNode: nodeParameters present ==========================

    @Nested
    class WhenNodeParametersPresent {

        @Test
        void returnsEvaluatedParamsAsNodeResponse() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode nodeParams = MAPPER.createObjectNode();
            nodeParams.put("orderId", "ORD-123");
            nodeParams.put("customerId", "CUST-456");
            context.set("nodeParameters", nodeParams);

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertNotNull(response.getNodeResponse());
            assertEquals("ORD-123", response.getNodeResponse().get("orderId").asText());
            assertEquals("CUST-456", response.getNodeResponse().get("customerId").asText());
        }

        @Test
        void alwaysReturnsRunningStatus() {
            ObjectNode context = MAPPER.createObjectNode();
            context.set("nodeParameters", MAPPER.createObjectNode().put("key", "value"));

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertEquals(WorkflowStatus.RUNNING, response.getWorkflowStatus());
        }

        @Test
        void handlesNestedObjectParameters() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode nodeParams = MAPPER.createObjectNode();
            ObjectNode nestedObj = MAPPER.createObjectNode();
            nestedObj.put("street", "123 Main St");
            nestedObj.put("city", "Bengaluru");
            nodeParams.set("address", nestedObj);
            nodeParams.put("name", "John");
            context.set("nodeParameters", nodeParams);

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            JsonNode addressNode = response.getNodeResponse().get("address");
            assertNotNull(addressNode);
            assertTrue(addressNode.isObject());
            assertEquals("123 Main St", addressNode.get("street").asText());
            assertEquals("Bengaluru", addressNode.get("city").asText());
            assertEquals("John", response.getNodeResponse().get("name").asText());
        }

        @Test
        void handlesArrayParameters() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode nodeParams = MAPPER.createObjectNode();
            nodeParams.set("tags", MAPPER.createArrayNode().add("urgent").add("vip"));
            context.set("nodeParameters", nodeParams);

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            JsonNode tags = response.getNodeResponse().get("tags");
            assertNotNull(tags);
            assertTrue(tags.isArray());
            assertEquals(2, tags.size());
            assertEquals("urgent", tags.get(0).asText());
            assertEquals("vip", tags.get(1).asText());
        }

        @Test
        void handlesNumericParameters() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode nodeParams = MAPPER.createObjectNode();
            nodeParams.put("retryCount", 3);
            nodeParams.put("amount", 99.99);
            context.set("nodeParameters", nodeParams);

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertEquals(3, response.getNodeResponse().get("retryCount").asInt());
            assertEquals(99.99, response.getNodeResponse().get("amount").asDouble(), 0.001);
        }

        @Test
        void handlesBooleanParameters() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode nodeParams = MAPPER.createObjectNode();
            nodeParams.put("isUrgent", true);
            nodeParams.put("isResolved", false);
            context.set("nodeParameters", nodeParams);

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertTrue(response.getNodeResponse().get("isUrgent").asBoolean());
            assertFalse(response.getNodeResponse().get("isResolved").asBoolean());
        }

        @Test
        void handlesNullValueInParameters() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode nodeParams = MAPPER.createObjectNode();
            nodeParams.put("orderId", "ORD-123");
            nodeParams.putNull("optionalField");
            context.set("nodeParameters", nodeParams);

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertEquals("ORD-123", response.getNodeResponse().get("orderId").asText());
            assertTrue(response.getNodeResponse().get("optionalField").isNull());
        }
    }

    // ========================== executeNode: nodeParameters absent ==========================

    @Nested
    class WhenNodeParametersMissing {

        @Test
        void returnsEmptyObjectNodeWhenNodeParametersKeyAbsent() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("someOtherField", "value");

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertNotNull(response.getNodeResponse());
            assertTrue(response.getNodeResponse().isObject());
            assertEquals(0, response.getNodeResponse().size());
        }

        @Test
        void returnsEmptyObjectNodeWhenNodeParametersIsNull() {
            ObjectNode context = MAPPER.createObjectNode();
            context.putNull("nodeParameters");

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertNotNull(response.getNodeResponse());
            assertTrue(response.getNodeResponse().isObject());
            assertEquals(0, response.getNodeResponse().size());
        }

        @Test
        void returnsRunningStatusWhenParametersAbsent() {
            ObjectNode context = MAPPER.createObjectNode();

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertEquals(WorkflowStatus.RUNNING, response.getWorkflowStatus());
        }

        @Test
        void returnsEmptyObjectNodeWhenContextIsEmpty() {
            ObjectNode context = MAPPER.createObjectNode();

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            assertNotNull(response.getNodeResponse());
            assertEquals(0, response.getNodeResponse().size());
        }
    }

    // ========================== executeNode: nodeParameters is empty ==========================

    @Test
    void returnsEmptyObjectNodeWhenNodeParametersIsEmptyObject() {
        ObjectNode context = MAPPER.createObjectNode();
        context.set("nodeParameters", MAPPER.createObjectNode());

        ActivityRequest<ChildNode> request = buildRequest(context);
        ActivityResponse response = childNodeActivity.executeNode(request);

        assertNotNull(response.getNodeResponse());
        assertTrue(response.getNodeResponse().isObject());
        assertEquals(0, response.getNodeResponse().size());
        assertEquals(WorkflowStatus.RUNNING, response.getWorkflowStatus());
    }

    // ========================== Response invariants ==========================

    @Test
    void responseNextNodeIsAlwaysNull() {
        ObjectNode context = MAPPER.createObjectNode();
        context.set("nodeParameters", MAPPER.createObjectNode().put("k", "v"));

        ActivityRequest<ChildNode> request = buildRequest(context);
        ActivityResponse response = childNodeActivity.executeNode(request);

        assertNull(response.getNextNode());
    }

    @Test
    void responseDispositionIsAlwaysNull() {
        ObjectNode context = MAPPER.createObjectNode();
        context.set("nodeParameters", MAPPER.createObjectNode().put("k", "v"));

        ActivityRequest<ChildNode> request = buildRequest(context);
        ActivityResponse response = childNodeActivity.executeNode(request);

        assertNull(response.getDisposition());
    }

    // ========================== NodeParameterEvaluator integration scenarios ==========================

    @Nested
    class NodeParameterEvaluatorScenarios {

        @Test
        void jsonPathResolvesSimpleField() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("orderId", "ORD-999");

            Map<String, String> parameters = Map.of("resolvedOrderId", "$.orderId");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertEquals("ORD-999", evaluated.get("resolvedOrderId").asText());
        }

        @Test
        void jsonPathResolvesNestedField() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode orderDetails = MAPPER.createObjectNode();
            orderDetails.put("orderId", "ORD-777");
            orderDetails.put("status", "DELIVERED");
            context.set("orderDetails", orderDetails);

            Map<String, String> parameters = Map.of(
                    "childOrderId", "$.orderDetails.orderId",
                    "childStatus", "$.orderDetails.status"
            );
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertEquals("ORD-777", evaluated.get("childOrderId").asText());
            assertEquals("DELIVERED", evaluated.get("childStatus").asText());
        }

        @Test
        void staticValuePassesThroughUnchanged() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("irrelevant", "data");

            Map<String, String> parameters = Map.of("region", "IN", "env", "production");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertEquals("IN", evaluated.get("region").asText());
            assertEquals("production", evaluated.get("env").asText());
        }

        @Test
        void mixedJsonPathAndStaticValues() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("customerId", "CUST-100");

            Map<String, String> parameters = new HashMap<>();
            parameters.put("resolvedCustomerId", "$.customerId");
            parameters.put("source", "child_workflow");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertEquals("CUST-100", evaluated.get("resolvedCustomerId").asText());
            assertEquals("child_workflow", evaluated.get("source").asText());
        }

        @Test
        void missingJsonPathResultsInNull() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("someField", "value");

            Map<String, String> parameters = Map.of("missing", "$.nonexistent.path");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertTrue(evaluated.has("missing"));
            assertTrue(evaluated.get("missing").isNull());
        }

        @Test
        void emptyParametersReturnsEmptyResult() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("data", "value");

            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, Map.of());

            assertEquals(0, evaluated.size());
        }

        @Test
        void nullParametersReturnsEmptyResult() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("data", "value");

            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, null);

            assertEquals(0, evaluated.size());
        }

        @Test
        void nullContextReturnsEmptyResult() {
            Map<String, String> parameters = Map.of("key", "$.field");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(null, parameters);

            assertEquals(0, evaluated.size());
        }

        @Test
        void emptyContextReturnsEmptyResult() {
            ObjectNode context = MAPPER.createObjectNode();
            Map<String, String> parameters = Map.of("key", "$.field");

            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertEquals(0, evaluated.size());
        }

        @Test
        void jsonPathResolvesNumericValue() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("retryLimit", 5);

            Map<String, String> parameters = Map.of("maxRetries", "$.retryLimit");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertEquals(5, evaluated.get("maxRetries").asInt());
        }

        @Test
        void jsonPathResolvesBooleanValue() {
            ObjectNode context = MAPPER.createObjectNode();
            context.put("isPriority", true);

            Map<String, String> parameters = Map.of("urgent", "$.isPriority");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertTrue(evaluated.get("urgent").asBoolean());
        }

        @Test
        void jsonPathResolvesArrayFromContext() {
            ObjectNode context = MAPPER.createObjectNode();
            context.set("items", MAPPER.createArrayNode().add("item1").add("item2"));

            Map<String, String> parameters = Map.of("childItems", "$.items");
            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);

            assertTrue(evaluated.get("childItems").isArray());
            assertEquals(2, evaluated.get("childItems").size());
        }

        @Test
        void endToEnd_evaluateAndUseInActivity() {
            ObjectNode context = MAPPER.createObjectNode();
            ObjectNode orderDetails = MAPPER.createObjectNode();
            orderDetails.put("orderId", "ORD-555");
            orderDetails.put("amount", 1500);
            context.set("orderDetails", orderDetails);
            context.put("issueType", "DELAY");

            Map<String, String> parameters = new HashMap<>();
            parameters.put("childOrderId", "$.orderDetails.orderId");
            parameters.put("childAmount", "$.orderDetails.amount");
            parameters.put("childIssue", "$.issueType");
            parameters.put("source", "parent_escalation");

            ObjectNode evaluated = NodeParameterEvaluator.evaluateNodeParameters(context, parameters);
            context.set("nodeParameters", evaluated);

            ActivityRequest<ChildNode> request = buildRequest(context);
            ActivityResponse response = childNodeActivity.executeNode(request);

            JsonNode nodeResponse = response.getNodeResponse();
            assertEquals("ORD-555", nodeResponse.get("childOrderId").asText());
            assertEquals(1500, nodeResponse.get("childAmount").asInt());
            assertEquals("DELAY", nodeResponse.get("childIssue").asText());
            assertEquals("parent_escalation", nodeResponse.get("source").asText());
            assertEquals(WorkflowStatus.RUNNING, response.getWorkflowStatus());
        }
    }

    // ========================== ChildNode model tests ==========================

    @Nested
    class ChildNodeModelTests {

        @Test
        void getTypeReturnsChild() {
            ChildNode childNode = new ChildNode();
            assertEquals(NodeType.CHILD, childNode.getType());
        }

        @Test
        void mergeRequestToEntity_allFieldsSet() {
            ChildNode target = createChildNode("wf-1", "v1", ExecutionMode.ASYNC);
            ChildNode source = createChildNode("wf-2", "v2", ExecutionMode.SYNC);

            target.mergeRequestToEntity(source);

            assertEquals("wf-2", target.getChildWorkflowId());
            assertEquals("v2", target.getChildWorkflowVersion());
            assertEquals(ExecutionMode.SYNC, target.getExecutionMode());
        }

        @Test
        void mergeRequestToEntity_nullFieldsDoNotOverwrite() {
            ChildNode target = createChildNode("wf-1", "v1", ExecutionMode.ASYNC);
            ChildNode source = new ChildNode();

            target.mergeRequestToEntity(source);

            assertEquals("wf-1", target.getChildWorkflowId());
            assertEquals("v1", target.getChildWorkflowVersion());
            assertEquals(ExecutionMode.ASYNC, target.getExecutionMode());
        }

        @Test
        void mergeRequestToEntity_partialUpdate() {
            ChildNode target = createChildNode("wf-1", "v1", ExecutionMode.ASYNC);
            ChildNode source = new ChildNode();
            source.setChildWorkflowVersion("v3");

            target.mergeRequestToEntity(source);

            assertEquals("wf-1", target.getChildWorkflowId());
            assertEquals("v3", target.getChildWorkflowVersion());
            assertEquals(ExecutionMode.ASYNC, target.getExecutionMode());
        }

        @Test
        void jsonDeserialization() throws Exception {
            String json = "{\"type\":\"CHILD\",\"id\":\"child-1\",\"name\":\"spawn_child\"," +
                    "\"executionMode\":\"ASYNC\",\"childWorkflowId\":\"wf-child\"," +
                    "\"childWorkflowVersion\":\"v1\"}";

            ChildNode node = MAPPER.readValue(json, ChildNode.class);

            assertEquals(NodeType.CHILD, node.getType());
            assertEquals("child-1", node.getId());
            assertEquals("spawn_child", node.getName());
            assertEquals(ExecutionMode.ASYNC, node.getExecutionMode());
            assertEquals("wf-child", node.getChildWorkflowId());
            assertEquals("v1", node.getChildWorkflowVersion());
        }

        @Test
        void jsonSerialization_roundTrip() throws Exception {
            ChildNode original = createChildNode("wf-child", "v2", ExecutionMode.ASYNC);
            original.setId("child-1");
            original.setName("my_child");

            String json = MAPPER.writeValueAsString(original);
            ChildNode deserialized = MAPPER.readValue(json, ChildNode.class);

            assertEquals(original.getChildWorkflowId(), deserialized.getChildWorkflowId());
            assertEquals(original.getChildWorkflowVersion(), deserialized.getChildWorkflowVersion());
            assertEquals(original.getExecutionMode(), deserialized.getExecutionMode());
            assertEquals(original.getId(), deserialized.getId());
            assertEquals(original.getName(), deserialized.getName());
            assertEquals(NodeType.CHILD, deserialized.getType());
        }
    }

    // ========================== Helpers ==========================

    private static ActivityRequest<ChildNode> buildRequest(ObjectNode context) {
        ChildNode childNode = createChildNode("child-wf-001", "v1", ExecutionMode.ASYNC);
        childNode.setId("child-node-1");
        childNode.setName("spawn_child");

        ActivityRequest<ChildNode> request = new ActivityRequest<>();
        request.setWorkflowId("parent-wf-001");
        request.setNodeDefinition(childNode);
        request.setContext(context);
        request.setIsTerminal(false);
        request.setThreadContext(Map.of("tenant", "cs"));
        return request;
    }

    private static ChildNode createChildNode(String workflowId, String version, ExecutionMode mode) {
        ChildNode node = new ChildNode();
        node.setChildWorkflowId(workflowId);
        node.setChildWorkflowVersion(version);
        node.setExecutionMode(mode);
        return node;
    }
}
