package com.flipkart.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-node retry policy sourced from the WorkflowNode DSL stored in HBase.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeRetryConfig {

    @JsonProperty("maxAttempts")
    private int maxAttempts;

    @JsonProperty("initialIntervalSeconds")
    private int initialIntervalSeconds = 1;

    @JsonProperty("maxIntervalSeconds")
    private int maxIntervalSeconds = 20;

    @JsonProperty("backoffCoefficient")
    private double backoffCoefficient = 2.0;
}
