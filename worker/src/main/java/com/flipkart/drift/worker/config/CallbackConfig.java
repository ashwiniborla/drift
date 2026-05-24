package com.flipkart.drift.worker.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallbackConfig {
    private boolean enabled = true;
    private int timeoutSeconds = 10;
    private int maxAttempts = 3;
    private int initialIntervalSeconds = 1;
    private double backoffCoefficient = 2.0;
    private int maxIntervalSeconds = 20;
}
