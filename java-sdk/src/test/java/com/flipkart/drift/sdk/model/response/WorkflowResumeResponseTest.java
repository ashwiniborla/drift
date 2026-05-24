package com.flipkart.drift.sdk.model.response;

import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowResumeResponseTest {

    @Test
    void testDefaultConstructor() {
        WorkflowResumeResponse response = new WorkflowResumeResponse();
        assertNull(response.getWorkflowId());
        assertNull(response.getWorkflowStatus());
    }

    @Test
    void testAllArgsConstructor() {
        WorkflowResumeResponse response = new WorkflowResumeResponse("wf-123", WorkflowStatus.RUNNING);
        assertEquals("wf-123", response.getWorkflowId());
        assertEquals(WorkflowStatus.RUNNING, response.getWorkflowStatus());
    }

    @Test
    void testBuilder() {
        WorkflowResumeResponse response = WorkflowResumeResponse.builder()
                .workflowId("wf-456")
                .workflowStatus(WorkflowStatus.RUNNING)
                .build();
        assertEquals("wf-456", response.getWorkflowId());
        assertEquals(WorkflowStatus.RUNNING, response.getWorkflowStatus());
    }

    @Test
    void testEquality() {
        WorkflowResumeResponse r1 = new WorkflowResumeResponse("wf-1", WorkflowStatus.RUNNING);
        WorkflowResumeResponse r2 = new WorkflowResumeResponse("wf-1", WorkflowStatus.RUNNING);
        assertEquals(r1, r2);
    }

    @Test
    void testSetters() {
        WorkflowResumeResponse response = new WorkflowResumeResponse();
        response.setWorkflowId("wf-789");
        response.setWorkflowStatus(WorkflowStatus.RUNNING);
        assertEquals("wf-789", response.getWorkflowId());
        assertEquals(WorkflowStatus.RUNNING, response.getWorkflowStatus());
    }

    @Test
    void testDoesNotExtendWorkflowResponse() {
        // WorkflowResumeResponse must not extend WorkflowResponse:
        // verified structurally — superclass is Object
        assertNotEquals(WorkflowResponse.class, WorkflowResumeResponse.class.getSuperclass(),
                "WorkflowResumeResponse must NOT extend WorkflowResponse");
    }
}
