package com.flipkart.drift.api.service;

import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.service.utils.Utility;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.workflows.GenericWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemporalServiceTest {

    @Mock
    private WorkflowClient mockClient;

    @Mock
    private RedisPubSubService redisPubSubService;

    @Mock
    private DriftConfiguration driftConfiguration;

    @Mock
    private Utility utility;

    private TemporalService service;

    private static final WorkflowExecution WF_EXEC =
            WorkflowExecution.newBuilder().setWorkflowId("wf-1").setRunId("run-1").build();

    @BeforeEach
    void setUp() {
        service = new TemporalService(redisPubSubService, driftConfiguration, utility);
    }

    // getWorkflowState

    @Test
    void getWorkflowState_success() {
        GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
        WorkflowState state = new WorkflowState();
        when(mockClient.newWorkflowStub(eq(GenericWorkflow.class), eq("wf-1"))).thenReturn(mockWorkflow);
        when(mockWorkflow.getWorkflowState()).thenReturn(state);

        WorkflowState result = service.getWorkflowState("wf-1");

        assertSame(state, result);
    }

    @Test
    void getWorkflowState_workflowExceptionWithCause_usesCauseMessage() {
        GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
        RuntimeException root = new RuntimeException("activity timed out");
        WorkflowNotFoundException wfEx = new WorkflowNotFoundException(WF_EXEC, "GenericWorkflow", root);
        when(mockClient.newWorkflowStub(eq(GenericWorkflow.class), eq("wf-1"))).thenReturn(mockWorkflow);
        when(mockWorkflow.getWorkflowState()).thenThrow(wfEx);

        ApiException ex = assertThrows(ApiException.class, () -> service.getWorkflowState("wf-1"));

        assertEquals("activity timed out", ex.getMessage());
        assertEquals(Response.Status.EXPECTATION_FAILED, ex.getStatus());
    }

    @Test
    void getWorkflowState_workflowExceptionWithoutCause_usesExceptionMessage() {
        GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
        WorkflowNotFoundException wfEx = new WorkflowNotFoundException(WF_EXEC, "GenericWorkflow", null);
        when(mockClient.newWorkflowStub(eq(GenericWorkflow.class), eq("wf-1"))).thenReturn(mockWorkflow);
        when(mockWorkflow.getWorkflowState()).thenThrow(wfEx);

        ApiException ex = assertThrows(ApiException.class, () -> service.getWorkflowState("wf-1"));

        assertEquals(wfEx.getMessage(), ex.getMessage());
        assertEquals(Response.Status.EXPECTATION_FAILED, ex.getStatus());
    }

    // executeDisconnectedNode

    @Test
    void executeDisconnectedNode_success() {
        GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
        WorkflowUtilityRequest request = new WorkflowUtilityRequest();
        request.setWorkflowId("wf-1");
        WorkflowUtilityResponse response = new WorkflowUtilityResponse();
        when(mockClient.newWorkflowStub(eq(GenericWorkflow.class), eq("wf-1"))).thenReturn(mockWorkflow);
        when(mockWorkflow.executeDisconnectedNode(request)).thenReturn(response);

        WorkflowUtilityResponse result = service.executeDisconnectedNode(request);

        assertSame(response, result);
    }

    @Test
    void executeDisconnectedNode_workflowExceptionWithCause_usesCauseMessage() {
        GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
        WorkflowUtilityRequest request = new WorkflowUtilityRequest();
        request.setWorkflowId("wf-1");
        RuntimeException root = new RuntimeException("node execution failed");
        WorkflowNotFoundException wfEx = new WorkflowNotFoundException(WF_EXEC, "GenericWorkflow", root);
        when(mockClient.newWorkflowStub(eq(GenericWorkflow.class), eq("wf-1"))).thenReturn(mockWorkflow);
        when(mockWorkflow.executeDisconnectedNode(request)).thenThrow(wfEx);

        ApiException ex = assertThrows(ApiException.class, () -> service.executeDisconnectedNode(request));

        assertEquals("node execution failed", ex.getMessage());
        assertEquals(Response.Status.EXPECTATION_FAILED, ex.getStatus());
    }

    @Test
    void executeDisconnectedNode_workflowExceptionWithoutCause_usesExceptionMessage() {
        GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
        WorkflowUtilityRequest request = new WorkflowUtilityRequest();
        request.setWorkflowId("wf-1");
        WorkflowNotFoundException wfEx = new WorkflowNotFoundException(WF_EXEC, "GenericWorkflow", null);
        when(mockClient.newWorkflowStub(eq(GenericWorkflow.class), eq("wf-1"))).thenReturn(mockWorkflow);
        when(mockWorkflow.executeDisconnectedNode(request)).thenThrow(wfEx);

        ApiException ex = assertThrows(ApiException.class, () -> service.executeDisconnectedNode(request));

        assertEquals(wfEx.getMessage(), ex.getMessage());
        assertEquals(Response.Status.EXPECTATION_FAILED, ex.getStatus());
    }
}
