package com.flipkart.drift.commons.model.resolvedDetails;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flipkart.drift.commons.model.enums.HttpContentTypeEnum;
import com.flipkart.drift.commons.model.enums.HttpMethod;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class HttpDetails implements ResolvedDetails {
    private final String url;
    private final HttpMethod method;
    private final HttpContentTypeEnum contentType;
    private final Map<String, String> queryParams;
    private final Map<String, Object> body;
    private Map<String, String> headers;
    private final String targetClientId;

    @Builder
    @JsonCreator
    public HttpDetails(@JsonProperty("url") String url,
                       @JsonProperty("method") HttpMethod method,
                       @JsonProperty("contentType") HttpContentTypeEnum contentType,
                       @JsonProperty("headers") Map<String, String> headers,
                       @JsonProperty("queryParams") Map<String, String> queryParams,
                       @JsonProperty("body") Map<String, Object> body,
                       @JsonProperty("targetClientId") String targetClientId) {
        this.url = url;
        this.method = method;
        this.contentType = contentType;
        this.headers = headers;
        this.queryParams = queryParams;
        this.body = body;
        this.targetClientId = targetClientId;

        // Simple proxy expects Content-Type in headers.
        if (null == this.headers) {
            this.headers = new HashMap<>();
        }
        if (contentType != null) {
            this.headers.put("Content-Type", contentType.toString());
        }
    }
}
