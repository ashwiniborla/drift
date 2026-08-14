package com.flipkart.drift.worker.workflows;

import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowNodeExecutorTest {

    private WorkflowState workflowState;
    private WorkflowNodeExecutor executor;

    @BeforeEach
    void setUp() {
        workflowState = new WorkflowState();
        // Workflow.getLogger() requires a Temporal execution context; mock it for unit tests
        try (MockedStatic<io.temporal.workflow.Workflow> temporal = mockStatic(io.temporal.workflow.Workflow.class)) {
            temporal.when(() -> io.temporal.workflow.Workflow.getLogger(any(Class.class)))
                    .thenReturn(mock(Logger.class));
            executor = new WorkflowNodeExecutor(workflowState);
        }
    }

    // --- helpers ---

    private WorkflowNode node(String instanceName) {
        WorkflowNode n = new WorkflowNode();
        n.setInstanceName(instanceName);
        return n;
    }

    private Workflow workflow(String defaultFailureNodeId, Map<String, WorkflowNode> states) {
        Workflow w = new Workflow();
        w.setDefaultFailureNode(defaultFailureNodeId);
        w.setStates(states);
        return w;
    }

    // --- tests ---

    @Test
    void defaultFailureNodeSelfFailure_terminatesWorkflow() {
        WorkflowNode fallbackNode = node("fallback");
        Workflow wf = workflow("fallback", Map.of("fallback", fallbackNode));

        ApplicationFailure ex = assertThrows(ApplicationFailure.class,
                () -> executor.handleNodeExecutionError(new RuntimeException("fallback exploded"), wf, fallbackNode));

        assertEquals("DEFAULT_FAILURE_NODE_FAILED", ex.getType());
        assertEquals(WorkflowStatus.FAILED, workflowState.getStatus());
    }

    @Test
    void normalNodeFailure_routesToFallback() {
        WorkflowNode normalNode = node("nodeA");
        WorkflowNode fallbackNode = node("fallback");
        Workflow wf = workflow("fallback", Map.of("fallback", fallbackNode));

        WorkflowNode result = executor.handleNodeExecutionError(new RuntimeException("nodeA failed"), wf, normalNode);

        assertSame(fallbackNode, result);
        assertNotEquals(WorkflowStatus.FAILED, workflowState.getStatus());
    }

    @Test
    void noFallbackConfigured_terminatesWorkflow() {
        WorkflowNode normalNode = node("nodeA");
        Workflow wf = workflow(null, Map.of());

        ApplicationFailure ex = assertThrows(ApplicationFailure.class,
                () -> executor.handleNodeExecutionError(new RuntimeException("nodeA failed"), wf, normalNode));

        assertEquals("NODE_EXECUTION_FAILED", ex.getType());
        assertEquals(WorkflowStatus.FAILED, workflowState.getStatus());
    }

    @Test
    void nullFailedNode_routesToFallback() {
        WorkflowNode fallbackNode = node("fallback");
        Workflow wf = workflow("fallback", Map.of("fallback", fallbackNode));

        WorkflowNode result = executor.handleNodeExecutionError(new RuntimeException("unknown"), wf, null);

        assertSame(fallbackNode, result);
    }

    @Test
    void errorMessageAlwaysSetOnException() {
        Workflow wf = workflow(null, Map.of());

        assertThrows(ApplicationFailure.class,
                () -> executor.handleNodeExecutionError(new RuntimeException("boom"), wf, node("nodeA")));

        assertTrue(workflowState.getErrorMessage().contains("boom"));
    }
}
