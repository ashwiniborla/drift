package com.flipkart.drift.worker.executor;

import com.flipkart.drift.commons.exception.HttpClientErrorException;
import com.flipkart.drift.commons.exception.HttpServerErrorException;
import com.flipkart.drift.commons.model.enums.HttpContentTypeEnum;
import com.flipkart.drift.commons.model.enums.HttpMethod;
import com.flipkart.drift.commons.model.resolvedDetails.HttpDetails;
import com.uber.m3.tally.Counter;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.Stopwatch;
import com.uber.m3.tally.Timer;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import retrofit2.Call;
import retrofit2.Response;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HttpExecutorTest {

    // -------------------------------------------------------------------------
    // 4xx tests — all should throw HttpClientErrorException
    // -------------------------------------------------------------------------

    @Test
    void execute_400Response_throwsHttpClientErrorException() throws Exception {
        verifyClientErrorThrown(400);
    }

    @Test
    void execute_404Response_throwsHttpClientErrorException() throws Exception {
        verifyClientErrorThrown(404);
    }

    @Test
    void execute_422Response_throwsHttpClientErrorException() throws Exception {
        verifyClientErrorThrown(422);
    }

    @Test
    void execute_429Response_throwsHttpClientErrorException() throws Exception {
        verifyClientErrorThrown(429);
    }

    @Test
    void execute_499Response_throwsHttpClientErrorException() throws Exception {
        verifyClientErrorThrown(499);
    }

    // -------------------------------------------------------------------------
    // 5xx tests — all should throw HttpServerErrorException
    // -------------------------------------------------------------------------

    @Test
    void execute_500Response_throwsHttpServerErrorException() throws Exception {
        verifyServerErrorThrown(500);
    }

    @Test
    void execute_502Response_throwsHttpServerErrorException() throws Exception {
        verifyServerErrorThrown(502);
    }

    @Test
    void execute_503Response_throwsHttpServerErrorException() throws Exception {
        verifyServerErrorThrown(503);
    }

    @Test
    void execute_504Response_throwsHttpServerErrorException() throws Exception {
        verifyServerErrorThrown(504);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void verifyClientErrorThrown(int statusCode) throws Exception {
        HttpClientErrorException ex = assertThrows(
                HttpClientErrorException.class,
                () -> buildExecutorAndExecute(statusCode)
        );
        assertEquals(statusCode, ex.getStatusCode());
        assertTrue(ex.getMessage().contains(String.valueOf(statusCode)),
                "Expected message to contain status code " + statusCode + " but was: " + ex.getMessage());
    }

    private void verifyServerErrorThrown(int statusCode) throws Exception {
        HttpServerErrorException ex = assertThrows(
                HttpServerErrorException.class,
                () -> buildExecutorAndExecute(statusCode)
        );
        assertEquals(statusCode, ex.getStatusCode());
        assertTrue(ex.getMessage().contains(String.valueOf(statusCode)),
                "Expected message to contain status code " + statusCode + " but was: " + ex.getMessage());
    }

    /**
     * Constructs an HttpExecutor via reflection (bypassing the Guava LRU cache),
     * injects a mocked HttpService, stubs Activity metrics, and calls execute().
     */
    @SuppressWarnings("unchecked")
    private void buildExecutorAndExecute(int statusCode) throws Exception {
        // 1. Build executor via private constructor (bypasses existingClient cache)
        Constructor<HttpExecutor> ctor = HttpExecutor.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        HttpExecutor executor = ctor.newInstance("http://test-service.example.com");

        // 2. Inject a mocked HttpService via reflection
        HttpService mockHttpService = mock(HttpService.class);
        Field httpServiceField = HttpExecutor.class.getDeclaredField("httpService");
        httpServiceField.setAccessible(true);
        httpServiceField.set(executor, mockHttpService);

        // 3. Mock the Retrofit Call and Response
        Call<ResponseBody> mockCall = mock(Call.class);
        Response<ResponseBody> mockResponse = mock(Response.class);
        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.code()).thenReturn(statusCode);
        // headers() returns okhttp3.Headers — use a real empty instance
        when(mockResponse.headers()).thenReturn(okhttp3.Headers.of());
        when(mockCall.execute()).thenReturn(mockResponse);

        // GET is the simplest method to stub
        when(mockHttpService.get(anyString(), anyMap(), anyMap())).thenReturn(mockCall);

        // 4. Stub Activity.getExecutionContext().getMetricsScope()
        Scope mockScope = mock(Scope.class);
        Counter mockCounter = mock(Counter.class);
        Timer mockTimer = mock(Timer.class);
        Stopwatch mockStopwatch = mock(Stopwatch.class);
        when(mockScope.tagged(any(Map.class))).thenReturn(mockScope);
        when(mockScope.counter(anyString())).thenReturn(mockCounter);
        when(mockScope.timer(anyString())).thenReturn(mockTimer);
        when(mockTimer.start()).thenReturn(mockStopwatch);

        ActivityExecutionContext mockContext = mock(ActivityExecutionContext.class);
        when(mockContext.getMetricsScope()).thenReturn(mockScope);

        // 5. Build a minimal HttpDetails (GET, no auth token — skips AuthNTokenGenerator)
        HttpDetails httpDetails = HttpDetails.builder()
                .url("http://test-service.example.com/api/v1/resource")
                .method(HttpMethod.GET)
                .contentType(HttpContentTypeEnum.APPLICATION_JSON)
                .headers(new HashMap<>())
                .queryParams(new HashMap<>())
                .body(new HashMap<>())
                .targetClientId(null)
                .build();

        // 6. Execute inside MockedStatic<Activity>
        try (MockedStatic<Activity> mockedActivity = mockStatic(Activity.class)) {
            mockedActivity.when(Activity::getExecutionContext).thenReturn(mockContext);
            executor.execute(httpDetails, "test-api");
        }
    }
}
