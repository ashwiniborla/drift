package com.flipkart.drift.commons.exception;

import lombok.Getter;

/**
 * Thrown by HttpExecutor when the upstream service responds with a 5xx status code
 * (or any unexpected non-2xx, non-4xx code).
 * This is a retryable error: the upstream may be temporarily unavailable and the
 * activity should be retried per the node's configured {@code retryConfig}.
 *
 * <p>Callers (e.g. HttpNodeNodeActivityImpl) should let this propagate through
 * {@code Activity.wrap(e)} so Temporal retries the activity per RetryOptions.</p>
 */
@Getter
public class HttpServerErrorException extends RuntimeException {

    private final int statusCode;

    public HttpServerErrorException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpServerErrorException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
