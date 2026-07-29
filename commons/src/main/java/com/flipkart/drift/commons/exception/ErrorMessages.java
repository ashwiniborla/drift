package com.flipkart.drift.commons.exception;

public final class ErrorMessages {

    private ErrorMessages() {}

    public static final String TIMEOUT_SECONDS_MUST_BE_POSITIVE =
            "timeoutSeconds must be > 0 for node: %s";
    public static final String MAX_ATTEMPTS_MUST_BE_AT_LEAST_ONE =
            "maxAttempts must be >= 1 for node: %s";
    public static final String INITIAL_INTERVAL_MUST_BE_POSITIVE =
            "initialIntervalSeconds must be > 0 for node: %s";
    public static final String MAX_INTERVAL_MUST_BE_POSITIVE =
            "maxIntervalSeconds must be > 0 for node: %s";
    public static final String BACKOFF_COEFFICIENT_MUST_BE_AT_LEAST_ONE =
            "backoffCoefficient must be >= 1.0 for node: %s";

    public static final String FIELD_MUST_BE_POSITIVE = "%s must be > 0, got: %s";
    public static final String BACKOFF_COEFFICIENT_INVALID = "backoffCoefficient must be >= 1.0, got: %s";
}
