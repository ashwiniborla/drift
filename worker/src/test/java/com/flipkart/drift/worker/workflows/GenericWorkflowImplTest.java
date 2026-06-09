package com.flipkart.drift.worker.workflows;

import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkflowNodeExecutor.handleNodeExecutionError.
 *
 * WorkflowNodeExecutor contains a field initializer that calls
 * io.temporal.workflow.Workflow.getLogger(), which requires a live Temporal
 * workflow context and cannot be called from a plain JUnit test. We bypass
 * this by injecting a mock Logger via reflection after construction so that
 * the field is replaced before any test method runs. handleNodeExecutionError
 * itself does not call the logger, so this is safe for the methods under test.
 *
 * The four test cases cover:
 *   1. GROOVY as defaultFailureNode — status FAILED + correct currentNodeRef
 *   2. FAILURE-type defaultFailureNode (regression) — currentNodeRef is the failing node
 *   3. defaultFailureNode itself throws / no fallback — ApplicationFailure thrown, status FAILED
 *   4. defaultFailureNode key present but missing from states map — same as no fallback
 */
class GenericWorkflowImplTest {

    /**
     * Helper: allocate a WorkflowNodeExecutor via Objenesis (skips all field initializers
     * and constructors), then inject the required fields via reflection.
     *
     * WorkflowNodeExecutor has a field initializer that calls
     * io.temporal.workflow.Workflow.getLogger() — a Temporal static method that throws
     * IllegalStateException outside a Temporal workflow context. Objenesis allocation
     * bypasses it so we can test handleNodeExecutionError in isolation.
     */
    private WorkflowNodeExecutor createExecutorWithMockLogger(WorkflowState state) throws Exception {
        ObjenesisStd objenesis = new ObjenesisStd();
        WorkflowNodeExecutor executor = objenesis.newInstance(WorkflowNodeExecutor.class);

        // Inject workflowState
        Field wsField = WorkflowNodeExecutor.class.getDeclaredField("workflowState");
        wsField.setAccessible(true);
        wsField.set(executor, state);

        // Inject mock logger (handleNodeExecutionError does not log, but the field must be non-null
        // if any other method is called during tests — defensive init)
        Field loggerField = WorkflowNodeExecutor.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(executor, Mockito.mock(Logger.class));

        // Inject localActivityTypes (also a field initializer, bypassed by objenesis)
        Field latField = WorkflowNodeExecutor.class.getDeclaredField("localActivityTypes");
        latField.setAccessible(true);
        latField.set(executor, com.google.common.collect.Sets.newHashSet(
                com.flipkart.drift.commons.model.enums.NodeType.INSTRUCTION,
                com.flipkart.drift.commons.model.enums.NodeType.BRANCH,
                com.flipkart.drift.commons.model.enums.NodeType.GROOVY,
                com.flipkart.drift.commons.model.enums.NodeType.SUCCESS,
                com.flipkart.drift.commons.model.enums.NodeType.FAILURE));

        return executor;
    }

    // -------------------------------------------------------------------------
    // Test 1: GROOVY as defaultFailureNode
    // handleNodeExecutionError must set status=FAILED and currentNodeRef to the
    // FAILING node (not the GROOVY fallback), before returning the fallback node.
    // -------------------------------------------------------------------------
    @Test
    void handleNodeExecutionError_groovyDefaultFailureNode_setsFailedStatusAndCorrectCurrentNodeRef()
            throws Exception {
        WorkflowState state = new WorkflowState();
        state.setWorkflowId("wf-groovy-001");

        WorkflowNode httpNode = new WorkflowNode();
        httpNode.setInstanceName("start-http-node");

        WorkflowNode groovyFallback = new WorkflowNode();
        groovyFallback.setInstanceName("groovy-failure-node");

        Map<String, WorkflowNode> states = new HashMap<>();
        states.put("start-http-node", httpNode);
        states.put("groovy-failure-node", groovyFallback);

        Workflow workflow = new Workflow();
        workflow.setDefaultFailureNode("groovy-failure-node");
        workflow.setStates(states);

        WorkflowNodeExecutor executor = createExecutorWithMockLogger(state);
        WorkflowNode result = executor.handleNodeExecutionError(
                new RuntimeException("HTTP call failed"), httpNode, workflow);

        assertEquals(WorkflowStatus.FAILED, state.getStatus(),
                "status must be FAILED immediately — before defaultFailureNode executes");
        assertEquals("start-http-node", state.getCurrentNodeRef(),
                "currentNodeRef must point to the FAILING node (start-http-node), not the GROOVY fallback");
        assertTrue(state.getErrorMessage().contains("HTTP call failed"),
                "errorMessage must contain the original exception message");
        assertSame(groovyFallback, result,
                "returned node must be the groovy fallback");
    }

    // -------------------------------------------------------------------------
    // Test 2: FAILURE-type defaultFailureNode (regression)
    // currentNodeRef must still be the failing HTTP node, not the FAILURE node.
    // -------------------------------------------------------------------------
    @Test
    void handleNodeExecutionError_failureTypeDefaultNode_currentNodeRefIsFailingHttpNode()
            throws Exception {
        WorkflowState state = new WorkflowState();
        state.setWorkflowId("wf-failure-002");

        WorkflowNode httpNode = new WorkflowNode();
        httpNode.setInstanceName("start-http-node");

        WorkflowNode failureNode = new WorkflowNode();
        failureNode.setInstanceName("failure-node");

        Map<String, WorkflowNode> states = new HashMap<>();
        states.put("start-http-node", httpNode);
        states.put("failure-node", failureNode);

        Workflow workflow = new Workflow();
        workflow.setDefaultFailureNode("failure-node");
        workflow.setStates(states);

        WorkflowNodeExecutor executor = createExecutorWithMockLogger(state);
        WorkflowNode result = executor.handleNodeExecutionError(
                new RuntimeException("timeout"), httpNode, workflow);

        assertEquals(WorkflowStatus.FAILED, state.getStatus());
        assertEquals("start-http-node", state.getCurrentNodeRef(),
                "regression: currentNodeRef must be the failing HTTP node, not failure-node");
        assertSame(failureNode, result);
    }

    // -------------------------------------------------------------------------
    // Test 3: defaultFailureNode itself throws / no fallback configured
    // When defaultFailureNode is null, ApplicationFailure must be thrown and
    // status must still be FAILED (moved to before the null check).
    // -------------------------------------------------------------------------
    @Test
    void handleNodeExecutionError_noFallbackConfigured_throwsApplicationFailureWithFailedStatus()
            throws Exception {
        WorkflowState state = new WorkflowState();
        state.setWorkflowId("wf-nofallback-003");

        WorkflowNode httpNode = new WorkflowNode();
        httpNode.setInstanceName("start-http-node");

        Map<String, WorkflowNode> states = new HashMap<>();
        states.put("start-http-node", httpNode);

        Workflow workflow = new Workflow();
        workflow.setDefaultFailureNode(null);
        workflow.setStates(states);

        WorkflowNodeExecutor executor = createExecutorWithMockLogger(state);

        ApplicationFailure thrown = assertThrows(ApplicationFailure.class,
                () -> executor.handleNodeExecutionError(
                        new RuntimeException("no fallback configured"), httpNode, workflow),
                "must throw ApplicationFailure when no defaultFailureNode is configured");

        assertEquals("NODE_EXECUTION_FAILED", thrown.getType(),
                "failure type must be NODE_EXECUTION_FAILED");
        assertEquals(WorkflowStatus.FAILED, state.getStatus(),
                "status must be FAILED even on the no-fallback path");
    }

    // -------------------------------------------------------------------------
    // Test 4: No defaultFailureNode — key present but not in states map
    // When the key resolves to null in the states map, same behaviour as no fallback.
    // -------------------------------------------------------------------------
    @Test
    void handleNodeExecutionError_defaultFailureNodeKeyMissingFromStates_throwsApplicationFailure()
            throws Exception {
        WorkflowState state = new WorkflowState();
        state.setWorkflowId("wf-missing-004");

        WorkflowNode httpNode = new WorkflowNode();
        httpNode.setInstanceName("http-node");

        Map<String, WorkflowNode> states = new HashMap<>();
        states.put("http-node", httpNode);
        // defaultFailureNode key is set but not present in states map
        Workflow workflow = new Workflow();
        workflow.setDefaultFailureNode("non-existent-node");
        workflow.setStates(states);

        WorkflowNodeExecutor executor = createExecutorWithMockLogger(state);

        ApplicationFailure thrown = assertThrows(ApplicationFailure.class,
                () -> executor.handleNodeExecutionError(
                        new RuntimeException("err"), httpNode, workflow));

        assertEquals("NODE_EXECUTION_FAILED", thrown.getType());
        assertEquals(WorkflowStatus.FAILED, state.getStatus(),
                "FAILED must be set even when the fallback node key is missing from states");
    }
}
