package com.flipkart.drift.api.config;

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
public class WorkerInvalidationConfig {
    private String headlessServiceHost = "drift-worker-headless";
    private int adminPort = 7201;
    private int perPodTimeoutMs = 2000;
    private boolean enabled = true;
}
