package com.flipkart.drift.worker.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform-level activity defaults bound from the {@code activityDefaults} YAML block.
 * <p>
 * Default values (maxAttempts=1, timeoutSeconds=10) match the values previously
 * hardcoded in {@code OptionsStore.activityOptionsV1}, so omitting this config block
 * is fully backward-compatible with existing deployments.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityDefaultsConfig {

    @JsonProperty("defaultMaxAttempts")
    private int defaultMaxAttempts = 1;

    // 10s was the pre-existing hardcoded startToCloseTimeout — preserved here for backward compatibility
    @JsonProperty("defaultTimeoutSeconds")
    private int defaultTimeoutSeconds = 10;
}
