package com.flipkart.drift.api.client;

import com.flipkart.drift.api.config.WorkerInvalidationConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Singleton
public class WorkerCacheInvalidationClient {

    public enum CacheType {
        NODE, WORKFLOW
    }

    private final HttpClient httpClient;
    private final WorkerInvalidationConfig config;

    @Inject
    public WorkerCacheInvalidationClient(WorkerInvalidationConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getPerPodTimeoutMs()))
                .build();
    }

    /**
     * Fan out a cache-invalidate POST to all live worker pod IPs.
     * Best-effort: per-pod errors are logged as WARN and do not block DSL publish.
     *
     * @param type the cache type (NODE or WORKFLOW)
     * @param key  the cache key to invalidate (non-null, non-blank)
     */
    // PROBE::redis-removal-api-temporal-async::ENTRY
    public void invalidate(CacheType type, String key) {
        if (!config.isEnabled()) {
            log.debug("feature=redis-removal op=cacheInvalidate type={} key={} skipped=disabled", type, key);
            return;
        }

        InetAddress[] podAddresses;
        try {
            podAddresses = InetAddress.getAllByName(config.getHeadlessServiceHost());
        } catch (Exception e) {
            log.warn("feature=redis-removal op=cacheInvalidate type={} key={} dnsResolutionFailed={}", type, key, e.getMessage());
            return;
        }

        for (InetAddress podAddress : podAddresses) {
            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
            String url = "http://" + podAddress.getHostAddress() + ":" + config.getAdminPort()
                    + "/tasks/cache-invalidate?type=" + type.name() + "&key=" + encodedKey;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(config.getPerPodTimeoutMs()))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    log.debug("feature=redis-removal op=cacheInvalidate podIp={} type={} key={} status=OK",
                            podAddress.getHostAddress(), type, key);
                } else {
                    log.warn("feature=redis-removal op=cacheInvalidate podIp={} type={} key={} statusCode={}",
                            podAddress.getHostAddress(), type, key, response.statusCode());
                }
            } catch (Exception e) {
                log.warn("feature=redis-removal op=cacheInvalidate podIp={} type={} key={} error={}",
                        podAddress.getHostAddress(), type, key, e.getMessage());
            }
        }
        // PROBE::redis-removal-api-temporal-async::EXIT
    }
}
