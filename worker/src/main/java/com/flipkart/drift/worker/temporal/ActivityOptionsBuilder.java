package com.flipkart.drift.worker.temporal;

import com.flipkart.drift.commons.model.node.NodeRetryConfig;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.worker.config.ActivityDefaultsConfig;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

public class ActivityOptionsBuilder {

    private static final int FALLBACK_TIMEOUT_SECONDS = 10;
    private static final int FALLBACK_MAX_ATTEMPTS = 1;
    private static final int FALLBACK_INITIAL_INTERVAL_SECONDS = 1;
    private static final int FALLBACK_MAX_INTERVAL_SECONDS = 20;
    private static final double FALLBACK_BACKOFF_COEFFICIENT = 2.0;

    private final int defaultTimeoutSeconds;
    private final int defaultMaxAttempts;

    public ActivityOptionsBuilder(ActivityDefaultsConfig defaults) {
        if (defaults != null) {
            this.defaultTimeoutSeconds = defaults.getDefaultTimeoutSeconds();
            this.defaultMaxAttempts = defaults.getDefaultMaxAttempts();
        } else {
            this.defaultTimeoutSeconds = FALLBACK_TIMEOUT_SECONDS;
            this.defaultMaxAttempts = FALLBACK_MAX_ATTEMPTS;
        }
    }

    public ActivityOptions build(WorkflowNode node) {
        NodeRetryConfig retryConfig = node.getRetryConfig();

        int timeout = node.getTimeoutSeconds() != null
                ? node.getTimeoutSeconds()
                : this.defaultTimeoutSeconds;

        int maxAttempts = retryConfig != null
                ? retryConfig.getMaxAttempts()
                : this.defaultMaxAttempts;

        int initialInterval = retryConfig != null
                ? retryConfig.getInitialIntervalSeconds()
                : FALLBACK_INITIAL_INTERVAL_SECONDS;

        int maxInterval = retryConfig != null
                ? retryConfig.getMaxIntervalSeconds()
                : FALLBACK_MAX_INTERVAL_SECONDS;

        double backoff = retryConfig != null
                ? retryConfig.getBackoffCoefficient()
                : FALLBACK_BACKOFF_COEFFICIENT;

        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(timeout))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(maxAttempts)
                        .setInitialInterval(Duration.ofSeconds(initialInterval))
                        .setMaximumInterval(Duration.ofSeconds(maxInterval))
                        .setBackoffCoefficient(backoff)
                        .build())
                .build();
    }
}
