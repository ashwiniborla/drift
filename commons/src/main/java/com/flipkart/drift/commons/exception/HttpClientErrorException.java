package com.flipkart.drift.commons.exception;

import lombok.Getter;

/**
 * Thrown by HttpExecutor when the upstream service responds with a 4xx status code.
 * This is a non-retryable error: retrying an identical request to a service that
 * already rejected it will not produce a different outcome.
 *
 * <p>Callers (e.g. HttpNodeNodeActivityImpl) must catch this explicitly and convert
 * it to {@code ApplicationFailure.newNonRetryableFailure} so Temporal skips retries.</p>
 */
@Getter
public class HttpClientErrorException extends RuntimeException {

    private final int statusCode;

    public HttpClientErrorException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpClientErrorException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
