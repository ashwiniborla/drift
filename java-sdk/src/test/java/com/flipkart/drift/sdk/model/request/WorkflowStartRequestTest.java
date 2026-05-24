package com.flipkart.drift.sdk.model.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStartRequestTest {

    @Test
    void testCallbackUrlFieldExists() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        assertNull(request.getCallbackUrl(), "callbackUrl should default to null (no @NotNull)");
    }

    @Test
    void testCallbackUrlCanBeSet() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl("https://example.com/callback");
        assertEquals("https://example.com/callback", request.getCallbackUrl());
    }

    @Test
    void testCallbackUrlNullable() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setCallbackUrl(null);
        assertNull(request.getCallbackUrl());
    }
}
