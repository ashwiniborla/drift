package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.utils.Constants;
import com.flipkart.drift.commons.utils.MetricsRegistry;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.worker.model.callback.CallbackPayload;
import com.codahale.metrics.Timer;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
public class CallbackActivityImpl implements CallbackActivity {

    // Package-private for test injection; non-final to allow mock injection in unit tests
    static HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String METRIC_ATTEMPTS = "callback.attempts";
    private static final String METRIC_SUCCESS = "callback.success";
    private static final String METRIC_FAILURE = "callback.failure";
    private static final String METRIC_LATENCY = "callback.latency";

    @Override
    public void sendCallback(String callbackUrl, CallbackPayload payload) {
        log.info("op=sendCallback callbackUrl={} workflowId={}",
                callbackUrl, payload.getWorkflowId());

        MetricsRegistry.incrementCounter(METRIC_ATTEMPTS);
        Timer.Context timerCtx = MetricsRegistry.timerContext(METRIC_LATENCY);

        try {
            String jsonBody = Constants.MAPPER.writeValueAsString(payload);
            String callbackEvent = resolveCallbackEvent(payload.getWorkflowStatus());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Drift-Workflow-Id", payload.getWorkflowId())
                    .header("X-Drift-Callback-Event", callbackEvent)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                MetricsRegistry.incrementCounter(METRIC_SUCCESS);
                log.info("op=sendCallback workflowId={} responseCode={} durationMs={}",
                        payload.getWorkflowId(), statusCode, timerCtx.stop() / 1_000_000);
            } else if (statusCode >= 400 && statusCode < 500) {
                MetricsRegistry.incrementCounter(METRIC_FAILURE);
                log.error("op=sendCallback workflowId={} statusCode={} reason=NON_RETRYABLE",
                        payload.getWorkflowId(), statusCode);
                timerCtx.stop();
                throw ApplicationFailure.newNonRetryableFailure(
                        "Callback rejected with HTTP " + statusCode, "CallbackRejected");
            } else {
                MetricsRegistry.incrementCounter(METRIC_FAILURE);
                log.error("op=sendCallback workflowId={} statusCode={} reason=RETRYABLE",
                        payload.getWorkflowId(), statusCode);
                timerCtx.stop();
                throw Activity.wrap(new RuntimeException("Callback failed with HTTP " + statusCode));
            }
        } catch (ApplicationFailure e) {
            throw e;
        } catch (Exception e) {
            MetricsRegistry.incrementCounter(METRIC_FAILURE);
            log.error("op=sendCallback workflowId={} reason=RETRYABLE error={}",
                    payload.getWorkflowId(), e.getMessage(), e);
            timerCtx.stop();
            throw Activity.wrap(e);
        }
    }

    private String resolveCallbackEvent(WorkflowStatus status) {
        if (status == null) {
            return "WORKFLOW_COMPLETED";
        }
        return switch (status) {
            case COMPLETED, ASYNC_COMPLETE -> "WORKFLOW_COMPLETED";
            case FAILED -> "WORKFLOW_FAILED";
            case DELEGATED -> "WORKFLOW_DELEGATED";
            default -> "WORKFLOW_COMPLETED";
        };
    }
}
