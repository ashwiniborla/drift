package com.flipkart.drift.commons.model.temporal;

import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStateTest {

    @Test
    void testCallbackUrlFieldExists() {
        WorkflowState state = new WorkflowState();
        assertNull(state.getCallbackUrl(), "callbackUrl should default to null");
    }

    @Test
    void testCallbackUrlCanBeSet() {
        WorkflowState state = new WorkflowState();
        state.setCallbackUrl("https://example.com/callback");
        assertEquals("https://example.com/callback", state.getCallbackUrl());
    }

    @Test
    void testCallbackUrlNullable() {
        WorkflowState state = new WorkflowState();
        state.setCallbackUrl(null);
        assertNull(state.getCallbackUrl());
    }

    @Test
    void testImplementsSerializable() {
        WorkflowState state = new WorkflowState();
        assertTrue(state instanceof Serializable, "WorkflowState must implement Serializable");
    }

    @Test
    void testExistingFieldsUnchanged() {
        WorkflowState state = new WorkflowState();
        state.setWorkflowId("wf-1");
        state.setIncidentId("inc-1");
        state.setStatus(WorkflowStatus.CREATED);
        state.setDisposition("DISPOSED");
        state.setErrorMessage("error");
        state.setCurrentNodeRef("node-1");

        assertEquals("wf-1", state.getWorkflowId());
        assertEquals("inc-1", state.getIncidentId());
        assertEquals(WorkflowStatus.CREATED, state.getStatus());
        assertEquals("DISPOSED", state.getDisposition());
        assertEquals("error", state.getErrorMessage());
        assertEquals("node-1", state.getCurrentNodeRef());
    }
}
