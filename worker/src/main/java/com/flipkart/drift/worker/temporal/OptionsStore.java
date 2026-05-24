package com.flipkart.drift.worker.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.worker.WorkflowImplementationOptions;

import java.time.Duration;

public class OptionsStore {
    public static final WorkflowImplementationOptions workflowImplementationOptions =
            WorkflowImplementationOptions.newBuilder()
                    .setFailWorkflowExceptionTypes(RuntimeException.class)
                    .build();

    public static final RetryOptions activityRetryOptions = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setMaximumInterval(Duration.ofSeconds(20))
            .setBackoffCoefficient(2)
            .setMaximumAttempts(3)
            .build();
    public static final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30L * 60))
            .setRetryOptions(activityRetryOptions)
            .build();

    public static final RetryOptions activityRetryOptionsV1 = RetryOptions.newBuilder()
            .setMaximumAttempts(1)
            .build();
    public static final ActivityOptions activityOptionsV1 = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(activityRetryOptionsV1)
            .build();

    public static final LocalActivityOptions localActivityOptions = LocalActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(activityRetryOptionsV1)
            .build();

    public static final RetryOptions callbackActivityRetryOptions = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setMaximumInterval(Duration.ofSeconds(20))
            .setBackoffCoefficient(2.0)
            .setMaximumAttempts(3)
            .build();
    public static final ActivityOptions callbackActivityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(90))
            .setRetryOptions(callbackActivityRetryOptions)
            .build();

    private OptionsStore() {
    }
}
