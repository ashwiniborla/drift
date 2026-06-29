package com.flipkart.drift.worker.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.flipkart.drift.commons.model.resolvedDetails.HttpDetails;
import com.flipkart.drift.worker.util.AuthNTokenGenerator;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.Stopwatch;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.flipkart.drift.commons.model.enums.HttpContentTypeEnum.APPLICATION_X_WWW_FORM_URLENCODED;

@Slf4j
public class HttpExecutor {
    private static final int MAX_CACHE_SIZE = 200;
    private static final Cache<String, HttpExecutor> existingClient = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)  // Uses LRU (Least Recently Used) eviction policy by default
            .removalListener(notification -> log.info("HTTP executor client evicted for baseUrl : {}", notification.getKey()))
            .build();
    
    private final HttpService httpService;
    private final ObjectMapper objectMapper;

    private HttpExecutor(String baseUrl) {
        log.info("HttpExecutor initialized for : {}", baseUrl);
        Retrofit retrofit = RetrofitClient.getClient(baseUrl);
        this.httpService = retrofit.create(HttpService.class);
        this.objectMapper = new ObjectMapper();
    }

    public static HttpExecutor getInstance(String url) {
        String baseUrl = extractBaseUrl(url);
        try {
            return existingClient.get(baseUrl, () -> new HttpExecutor(baseUrl));
        } catch (ExecutionException e) {
            log.error("Failed to get or create HTTP executor for baseUrl: {}", baseUrl, e);
            // Fallback to creating a new instance
            return new HttpExecutor(baseUrl);
        }
    }

    private static String extractBaseUrl(String urlString) {
        HttpUrl url = HttpUrl.parse(urlString);
        if (url != null) {
            return url.scheme() + "://" + url.host() + (url.port() != HttpUrl.defaultPort(url.scheme()) ? ":" + url.port() : "");
        } else {
            throw new IllegalArgumentException("Invalid URL: " + urlString);
        }
    }

    public JsonNode execute(HttpDetails httpDetails, String apiIdentifier) throws IOException {
        Scope metricsScope = Activity.getExecutionContext().getMetricsScope();
        String method = httpDetails.getMethod().name();
        
        // Create a child scope with URL and method tags
        Map<String, String> tags = new HashMap<>();
        tags.put("apiIdentifier", apiIdentifier);
        tags.put("method", method);
        Scope requestScope = metricsScope.tagged(tags);

        // Use counter for QPS measurement
        requestScope.counter("http_requests_total").inc(1);

        // Start the timer
        Stopwatch stopwatch = requestScope.timer("http_request_duration_ms").start();
        try {
            Call<ResponseBody> call;
            addAuthToken(httpDetails);
            Map<String, String> queryParams = httpDetails.getQueryParams() != null ? httpDetails.getQueryParams() : new HashMap<>();
            Map<String, Object> body = httpDetails.getBody() != null ? httpDetails.getBody() : new HashMap<>();
            switch (httpDetails.getMethod()) {
                case GET:
                    call = httpService.get(httpDetails.getUrl(), httpDetails.getHeaders(), queryParams);
                    break;
                case POST:
                    try {
                        log.info("Request body:" + Arrays.toString(body.entrySet().toArray()));
                    } catch (Exception e) {
                        log.error("Error while logging request body", e);
                    }
                    log.info(Arrays.toString(body.entrySet().toArray()));
                    if (APPLICATION_X_WWW_FORM_URLENCODED.equals(httpDetails.getContentType()))
                        call = httpService.post(httpDetails.getUrl(), httpDetails.getHeaders(), "client_credentials");
                    else
                        call = httpService.post(httpDetails.getUrl(), httpDetails.getHeaders(), queryParams, body);
                    break;
                case PUT:
                    call = httpService.put(httpDetails.getUrl(), httpDetails.getHeaders(), queryParams, body);
                    break;
                case DELETE:
                    call = httpService.delete(httpDetails.getUrl(), httpDetails.getHeaders(), queryParams);
                    break;
                default:
                    throw new UnsupportedOperationException("HTTP method not supported: " + httpDetails.getMethod());
            }

            Response<ResponseBody> response = call.execute();

            if (response.isSuccessful()) {
                // Record success metric
                requestScope.counter("http_requests_success").inc(1);
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        return JsonNodeFactory.instance.objectNode();
                    }
                    String bodyString = responseBody.string();
                    if (bodyString.isEmpty()) {
                        return JsonNodeFactory.instance.objectNode();
                    }
                    return objectMapper.readTree(bodyString);
                }
            } else {
                log.error("HTTP error code: " + response.code() + ", headers: " + response.headers().toString());
                // Record failure metric
                requestScope.counter("http_requests_failure").inc(1);
                throw new IOException("HTTP error code: " + response.code());
            }
        } catch (IOException e) {
            // Record failure metric for IO exceptions
            requestScope.counter("http_requests_failure").inc(1);
            throw e;
        } finally {
            // Stop the timer
            stopwatch.stop();
        }
    }

    private static void addAuthToken(HttpDetails httpDetails) {
        String targetClientId = httpDetails.getTargetClientId();
        if (targetClientId != null && !targetClientId.isEmpty() && !targetClientId.equals("null")) {
            String authToken = AuthNTokenGenerator.INSTANCE.getAuthToken(targetClientId);
            httpDetails.getHeaders().put("Authorization", authToken);
        }
    }
}

