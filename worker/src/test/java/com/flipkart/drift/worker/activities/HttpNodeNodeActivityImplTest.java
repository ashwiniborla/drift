package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.flipkart.drift.commons.exception.HttpClientErrorException;
import com.flipkart.drift.commons.exception.HttpServerErrorException;
import com.flipkart.drift.commons.model.enums.HttpContentTypeEnum;
import com.flipkart.drift.commons.model.enums.HttpMethod;
import com.flipkart.drift.commons.model.node.HttpNode;
import com.flipkart.drift.commons.model.resolvedDetails.HttpDetails;
import com.flipkart.drift.worker.executor.HttpExecutor;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.service.WorkflowConfigStoreService;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.worker.translator.ClientResolvedDetailBuilder;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for HttpNodeNodeActivityImpl's exception routing logic.
 *
 * <p>Strategy: invoke {@code activityImpl.executeNode(activityRequest)} directly
 * (NOT via the Temporal-generated stub). Mock {@code HttpExecutor.getInstance(...)}
 * via {@code MockedStatic<HttpExecutor>} and {@code ClientResolvedDetailBuilder.evaluateGroovy}
 * via {@code MockedStatic<ClientResolvedDetailBuilder>}.
 */
class HttpNodeNodeActivityImplTest {

    private HttpNodeNodeActivityImpl activityImpl;
    private WorkflowConfigStoreService mockConfigStoreService;

    @BeforeEach
    void setUp() {
        WorkflowContextHBService mockContextService = mock(WorkflowContextHBService.class);
        mockConfigStoreService = mock(WorkflowConfigStoreService.class);
        activityImpl = new HttpNodeNodeActivityImpl(mockContextService, mockConfigStoreService);
    }

    // -------------------------------------------------------------------------
    // 4xx tests — all should produce non-retryable ApplicationFailure
    // -------------------------------------------------------------------------

    @Test
    void executeNode_httpClientError404_throwsNonRetryableApplicationFailure() {
        assertNonRetryableApplicationFailure(404);
    }

    @Test
    void executeNode_httpClientError400_throwsNonRetryableApplicationFailure() {
        assertNonRetryableApplicationFailure(400);
    }

    @Test
    void executeNode_httpClientError422_throwsNonRetryableApplicationFailure() {
        assertNonRetryableApplicationFailure(422);
    }

    // -------------------------------------------------------------------------
    // Retryable 4xx (408, 429) — must NOT produce a non-retryable ApplicationFailure
    // -------------------------------------------------------------------------

    @Test
    void executeNode_httpServerError408_isNotNonRetryableApplicationFailure() {
        assertNotNonRetryableApplicationFailure(408);
    }

    @Test
    void executeNode_httpServerError429_isNotNonRetryableApplicationFailure() {
        assertNotNonRetryableApplicationFailure(429);
    }

    // -------------------------------------------------------------------------
    // 5xx tests — must NOT produce a non-retryable ApplicationFailure
    // -------------------------------------------------------------------------

    @Test
    void executeNode_httpServerError503_isNotNonRetryableApplicationFailure() {
        assertNotNonRetryableApplicationFailure(503);
    }

    @Test
    void executeNode_httpServerError500_isNotNonRetryableApplicationFailure() {
        assertNotNonRetryableApplicationFailure(500);
    }

    // -------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------

    private void assertNonRetryableApplicationFailure(int statusCode) {
        Throwable thrown = assertThrows(
                Throwable.class,
                () -> executeNodeWithMockedExecutor(new HttpClientErrorException("HTTP client error: " + statusCode, statusCode))
        );
        assertTrue(isNonRetryableApplicationFailure(thrown),
                "Expected a non-retryable ApplicationFailure in cause chain for status " + statusCode
                        + " but got: " + thrown.getClass().getName());
        // Verify the status code appears somewhere in the failure chain message
        assertTrue(containsStatusCode(thrown, statusCode),
                "Expected status code " + statusCode + " in failure message but was: " + thrown.getMessage());
    }

    private void assertNotNonRetryableApplicationFailure(int statusCode) {
        Throwable thrown = assertThrows(
                Throwable.class,
                () -> executeNodeWithMockedExecutor(new HttpServerErrorException("HTTP server error: " + statusCode, statusCode))
        );
        assertTrue(!isNonRetryableApplicationFailure(thrown),
                "Expected a retryable (non-ApplicationFailure or retryable) exception for status "
                        + statusCode + " but got non-retryable ApplicationFailure");
    }

    /**
     * Drives executeNode() with a mocked HttpExecutor that throws the given exception.
     */
    @SuppressWarnings("unchecked")
    private void executeNodeWithMockedExecutor(RuntimeException executorException) throws Exception {
        // Build a minimal HttpDetails for the mocked executor to return
        HttpDetails fakeHttpDetails = HttpDetails.builder()
                .url("http://fake-service.example.com/api/resource")
                .method(HttpMethod.GET)
                .contentType(HttpContentTypeEnum.APPLICATION_JSON)
                .headers(new HashMap<>())
                .queryParams(new HashMap<>())
                .body(new HashMap<>())
                .targetClientId(null)
                .build();

        // Build the ActivityRequest with a minimal HttpNode
        HttpNode httpNode = new HttpNode();
        httpNode.setId("test-node");
        httpNode.setVersion("1.0");

        ActivityRequest<HttpNode> activityRequest = ActivityRequest.<HttpNode>builder()
                .workflowId("test-workflow-id")
                .nodeDefinition(httpNode)
                .context(JsonNodeFactory.instance.objectNode())
                .threadContext(Collections.emptyMap())
                .build();

        // Stub workflowConfigStoreService.getEnumMapping() — called before HttpExecutor
        when(mockConfigStoreService.getEnumMapping()).thenReturn(Collections.emptyMap());

        // Mock HttpExecutor.getInstance(...) and the mocked executor's execute() call
        HttpExecutor mockHttpExecutor = mock(HttpExecutor.class);
        when(mockHttpExecutor.execute(any(HttpDetails.class), anyString())).thenThrow(executorException);

        try (MockedStatic<ClientResolvedDetailBuilder> mockedBuilder = mockStatic(ClientResolvedDetailBuilder.class);
             MockedStatic<HttpExecutor> mockedExecutorStatic = mockStatic(HttpExecutor.class)) {

            // Stub ClientResolvedDetailBuilder.evaluateGroovy for HttpDetails resolution
            mockedBuilder.when(() -> ClientResolvedDetailBuilder.evaluateGroovy(
                    any(), anyString(), any(JsonNode.class), eq(HttpDetails.class)
            )).thenReturn(fakeHttpDetails);

            // Stub HttpExecutor.getInstance to return our throwing mock
            mockedExecutorStatic.when(() -> HttpExecutor.getInstance(anyString())).thenReturn(mockHttpExecutor);

            activityImpl.executeNode(activityRequest);
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Walks the cause chain to find an ApplicationFailure with isNonRetryable() == true.
     */
    private boolean isNonRetryableApplicationFailure(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof ApplicationFailure && ((ApplicationFailure) current).isNonRetryable()) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) break; // guard against circular cause chains
            current = cause;
        }
        return false;
    }

    /**
     * Walks the cause chain to find the status code in any message.
     */
    private boolean containsStatusCode(Throwable t, int statusCode) {
        String code = String.valueOf(statusCode);
        Throwable current = t;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(code)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return false;
    }
}
