package com.flipkart.drift.api.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.persistence.bootstrap.CacheMaxEntriesConfig;
import com.flipkart.drift.persistence.bootstrap.StaticCacheRefreshConfig;
import io.dropwizard.Configuration;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriftConfiguration extends Configuration {
    @NotNull
    private ExecutorServiceConfig cacheRefreshExecutorServiceConfig;
    @NotNull
    private StaticCacheRefreshConfig staticCacheRefreshConfig;
    @NotNull
    private CacheMaxEntriesConfig cacheMaxEntriesConfig;
    @NotNull
    private String hbasePropertiesPath;
    @NotNull
    private String temporalFrontEnd;
    @NotNull
    private String temporalTaskQueue;
    @NotNull
    private String hadoopUserName;
    @NotNull
    private String hadoopLoginUser;

    // Optional: worker cache invalidation config for FR-3 fan-out
    // If absent from YAML, WorkflowClientModule falls back to new WorkerInvalidationConfig() defaults
    private WorkerInvalidationConfig workerInvalidationConfig;
}
