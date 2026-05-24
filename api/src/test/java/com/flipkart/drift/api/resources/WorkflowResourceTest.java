package com.flipkart.drift.api.resources;

import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.service.TemporalService;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.response.WorkflowStartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowResourceTest {

    @Mock
    private TemporalService temporalService;

    private WorkflowResource workflowResource;

    @BeforeEach
    void setUp() {
        workflowResource = new WorkflowResource(temporalService);
    }

    @Test
    void testStartWorkflow_returns202() {
        WorkflowStartResponse startResponse = WorkflowStartResponse.builder()
                .workflowId("wf-123")
                .workflowStatus(WorkflowStatus.CREATED)
                .build();
        when(temporalService.startWorkflow(any(WorkflowStartRequest.class))).thenReturn(startResponse);

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setWorkflowId("wf-123");

        Response response = workflowResource.startWorkflow(request);

        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus(),
                "startWorkflow must return HTTP 202 Accepted");
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof WorkflowStartResponse);
        WorkflowStartResponse body = (WorkflowStartResponse) response.getEntity();
        assertEquals("wf-123", body.getWorkflowId());
        assertEquals(WorkflowStatus.CREATED, body.getWorkflowStatus());
    }

    @Test
    void testStartWorkflow_nullCallbackUrl_accepted() {
        WorkflowStartResponse startResponse = WorkflowStartResponse.builder()
                .workflowId("wf-1")
                .workflowStatus(WorkflowStatus.CREATED)
                .build();
        when(temporalService.startWorkflow(any(WorkflowStartRequest.class))).thenReturn(startResponse);

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl(null);

        Response response = workflowResource.startWorkflow(request);
        assertEquals(202, response.getStatus());
    }

    @Test
    void testStartWorkflow_blankCallbackUrl_accepted() {
        WorkflowStartResponse startResponse = WorkflowStartResponse.builder()
                .workflowId("wf-1")
                .workflowStatus(WorkflowStatus.CREATED)
                .build();
        when(temporalService.startWorkflow(any(WorkflowStartRequest.class))).thenReturn(startResponse);

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl("");

        Response response = workflowResource.startWorkflow(request);
        assertEquals(202, response.getStatus());
    }

    @Test
    void testStartWorkflow_validHttpsCallbackUrl_accepted() {
        WorkflowStartResponse startResponse = WorkflowStartResponse.builder()
                .workflowId("wf-1")
                .workflowStatus(WorkflowStatus.CREATED)
                .build();
        when(temporalService.startWorkflow(any(WorkflowStartRequest.class))).thenReturn(startResponse);

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl("https://example.com/callback");

        Response response = workflowResource.startWorkflow(request);
        assertEquals(202, response.getStatus());
    }

    @Test
    void testStartWorkflow_ftpCallbackUrl_returns400() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl("ftp://example.com/callback");

        ApiException ex = assertThrows(ApiException.class,
                () -> workflowResource.startWorkflow(request));
        assertEquals(Response.Status.BAD_REQUEST, ex.getStatus(),
                "ftp:// callback URL must return 400 INVALID_CALLBACK_URL");
    }

    @Test
    void testStartWorkflow_invalidUri_returns400() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl("not a valid url %%");

        ApiException ex = assertThrows(ApiException.class,
                () -> workflowResource.startWorkflow(request));
        assertEquals(Response.Status.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void testStartWorkflow_httpWithNoHost_returns400() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl("http:///path");

        ApiException ex = assertThrows(ApiException.class,
                () -> workflowResource.startWorkflow(request));
        assertEquals(Response.Status.BAD_REQUEST, ex.getStatus());
    }
}
