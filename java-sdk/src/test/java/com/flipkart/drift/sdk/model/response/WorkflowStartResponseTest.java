package com.flipkart.drift.sdk.model.response;

import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStartResponseTest {

    @Test
    void testDefaultConstructor() {
        WorkflowStartResponse response = new WorkflowStartResponse();
        assertNull(response.getWorkflowId());
        assertNull(response.getWorkflowStatus());
    }

    @Test
    void testAllArgsConstructor() {
        WorkflowStartResponse response = new WorkflowStartResponse("wf-123", WorkflowStatus.CREATED);
        assertEquals("wf-123", response.getWorkflowId());
        assertEquals(WorkflowStatus.CREATED, response.getWorkflowStatus());
    }

    @Test
    void testBuilder() {
        WorkflowStartResponse response = WorkflowStartResponse.builder()
                .workflowId("wf-456")
                .workflowStatus(WorkflowStatus.CREATED)
                .build();
        assertEquals("wf-456", response.getWorkflowId());
        assertEquals(WorkflowStatus.CREATED, response.getWorkflowStatus());
    }

    @Test
    void testEquality() {
        WorkflowStartResponse r1 = new WorkflowStartResponse("wf-1", WorkflowStatus.CREATED);
        WorkflowStartResponse r2 = new WorkflowStartResponse("wf-1", WorkflowStatus.CREATED);
        assertEquals(r1, r2);
    }

    @Test
    void testSetters() {
        WorkflowStartResponse response = new WorkflowStartResponse();
        response.setWorkflowId("wf-789");
        response.setWorkflowStatus(WorkflowStatus.CREATED);
        assertEquals("wf-789", response.getWorkflowId());
        assertEquals(WorkflowStatus.CREATED, response.getWorkflowStatus());
    }

    @Test
    void testDoesNotExtendWorkflowResponse() {
        // WorkflowStartResponse must not extend WorkflowResponse:
        // verified structurally — superclass is Object
        assertNotEquals(WorkflowResponse.class, WorkflowStartResponse.class.getSuperclass(),
                "WorkflowStartResponse must NOT extend WorkflowResponse");
    }
}
