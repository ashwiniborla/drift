package com.flipkart.drift.commons.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {

    @Test
    void testAsyncAwaitChannelRemoved() throws Exception {
        List<String> fieldNames = Arrays.stream(Constants.Workflow.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());
        assertFalse(fieldNames.contains("ASYNC_AWAIT_CHANNEL"),
                "ASYNC_AWAIT_CHANNEL constant must be removed");
    }

    @Test
    void testDslUpdateChannelRemoved() throws Exception {
        List<String> fieldNames = Arrays.stream(Constants.Workflow.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());
        assertFalse(fieldNames.contains("DSL_UPDATE_CHANNEL"),
                "DSL_UPDATE_CHANNEL constant must be removed");
    }

    @Test
    void testRetainedConstantsPresent() {
        // GLOBAL and other constants must still be present
        assertEquals("_global", Constants.Workflow.GLOBAL);
        assertEquals("_response", Constants.Workflow.HTTP_RESPONSE);
        assertEquals("_enum_store", Constants.Workflow.ENUM_STORE);
        assertNotNull(Constants.Workflow.WORKFLOW_EXCEPTION);
        assertNotNull(Constants.Workflow.API_EXCEPTION);
    }

    @Test
    void testRedisStoreExceptionClassDoesNotExist() {
        try {
            Class.forName("com.flipkart.drift.commons.exception.RedisStoreException");
            fail("RedisStoreException class must be deleted");
        } catch (ClassNotFoundException e) {
            // Expected — class must not exist
        }
    }
}
