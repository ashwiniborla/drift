package com.flipkart.drift.worker.temporal;

import com.flipkart.drift.commons.model.node.NodeRetryConfig;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.worker.config.ActivityDefaultsConfig;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import lombok.NonNull;

import java.time.Duration;

public class ActivityOptionsBuilder {

    private final int defaultTimeoutSeconds;
    private final int defaultMaxAttempts;

    public ActivityOptionsBuilder(@NonNull ActivityDefaultsConfig defaults) {
        this.defaultTimeoutSeconds = defaults.getDefaultTimeoutSeconds();
        this.defaultMaxAttempts = defaults.getDefaultMaxAttempts();
    }

    public ActivityOptions build(WorkflowNode node) {
        int timeout = node.getTimeoutSeconds() != null
                ? node.getTimeoutSeconds()
                : this.defaultTimeoutSeconds;

        NodeRetryConfig retryConfig = node.getRetryConfig();
        int maxAttempts = retryConfig != null
                ? retryConfig.getMaxAttempts()
                : this.defaultMaxAttempts;

        // Interval/backoff: node retryConfig if present, else NodeRetryConfig field defaults (1s / 20s / 2.0)
        NodeRetryConfig effectiveRetry = retryConfig != null ? retryConfig : new NodeRetryConfig();

        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(timeout))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(maxAttempts)
                        .setInitialInterval(Duration.ofSeconds(effectiveRetry.getInitialIntervalSeconds()))
                        .setMaximumInterval(Duration.ofSeconds(effectiveRetry.getMaxIntervalSeconds()))
                        .setBackoffCoefficient(effectiveRetry.getBackoffCoefficient())
                        .build())
                .build();
    }
}
