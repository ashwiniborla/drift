package com.flipkart.drift.worker.activities;

import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.worker.model.callback.CallbackPayload;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallbackActivityImplTest {

    @Mock
    private HttpClient mockHttpClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private HttpResponse mockResponse;

    private CallbackActivityImpl callbackActivity;

    @BeforeEach
    void setUp() throws Exception {
        callbackActivity = new CallbackActivityImpl();
        // Inject mock HttpClient via reflection into static non-final field
        Field clientField = CallbackActivityImpl.class.getDeclaredField("HTTP_CLIENT");
        clientField.setAccessible(true);
        clientField.set(null, mockHttpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendCallback_2xxSuccess() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-123")
                .workflowStatus(WorkflowStatus.COMPLETED)
                .build();

        assertDoesNotThrow(() -> callbackActivity.sendCallback("http://example.com/callback", payload));
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendCallback_4xxThrowsNonRetryable() throws Exception {
        when(mockResponse.statusCode()).thenReturn(400);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-123")
                .workflowStatus(WorkflowStatus.COMPLETED)
                .build();

        ApplicationFailure ex = assertThrows(ApplicationFailure.class,
                () -> callbackActivity.sendCallback("http://example.com/callback", payload));
        assertTrue(ex.isNonRetryable(), "4xx errors must produce non-retryable ApplicationFailure");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendCallback_5xxThrowsRetryable() throws Exception {
        when(mockResponse.statusCode()).thenReturn(500);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-123")
                .workflowStatus(WorkflowStatus.COMPLETED)
                .build();

        // 5xx wraps in RuntimeException via Activity.wrap -> outside Temporal context throws RuntimeException
        assertThrows(RuntimeException.class,
                () -> callbackActivity.sendCallback("http://example.com/callback", payload));
    }

    @Test
    void testSendCallback_IOExceptionThrowsRetryable() throws Exception {
        doThrow(new java.io.IOException("Connection refused"))
                .when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-123")
                .workflowStatus(WorkflowStatus.FAILED)
                .errorMessage("something went wrong")
                .build();

        assertThrows(RuntimeException.class,
                () -> callbackActivity.sendCallback("http://example.com/callback", payload));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendCallback_headerCallbackEventCompleted() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-1")
                .workflowStatus(WorkflowStatus.COMPLETED)
                .build();

        callbackActivity.sendCallback("http://example.com/callback", payload);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any());
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("WORKFLOW_COMPLETED", capturedRequest.headers().firstValue("X-Drift-Callback-Event").orElse(""));
        assertEquals("wf-1", capturedRequest.headers().firstValue("X-Drift-Workflow-Id").orElse(""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendCallback_headerCallbackEventFailed() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-1")
                .workflowStatus(WorkflowStatus.FAILED)
                .errorMessage("timeout")
                .build();

        callbackActivity.sendCallback("http://example.com/callback", payload);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any());
        assertEquals("WORKFLOW_FAILED", requestCaptor.getValue().headers().firstValue("X-Drift-Callback-Event").orElse(""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendCallback_headerCallbackEventDelegated() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-1")
                .workflowStatus(WorkflowStatus.DELEGATED)
                .build();

        callbackActivity.sendCallback("http://example.com/callback", payload);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any());
        assertEquals("WORKFLOW_DELEGATED", requestCaptor.getValue().headers().firstValue("X-Drift-Callback-Event").orElse(""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendCallback_asyncCompleteIsWorkflowCompleted() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        CallbackPayload payload = CallbackPayload.builder()
                .workflowId("wf-1")
                .workflowStatus(WorkflowStatus.ASYNC_COMPLETE)
                .build();

        callbackActivity.sendCallback("http://example.com/callback", payload);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any());
        assertEquals("WORKFLOW_COMPLETED", requestCaptor.getValue().headers().firstValue("X-Drift-Callback-Event").orElse(""));
    }
}
