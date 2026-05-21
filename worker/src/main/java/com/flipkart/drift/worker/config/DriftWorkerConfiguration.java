package com.flipkart.drift.worker.config;

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
public class DriftWorkerConfiguration extends Configuration {
    @NotNull
    private RedisConfiguration redisConfiguration;
    @NotNull
    private ExecutorServiceConfig cacheRefreshExecutorServiceConfig;
    @NotNull
    private StaticCacheRefreshConfig staticCacheRefreshConfig;
    @NotNull
    private CacheMaxEntriesConfig cacheMaxEntriesConfig;
    @NotNull
    private String hbasePropertiesPath;
    @NotNull
    private String workflowPropertiesPath;
    @NotNull
    private String lookupPropertiesPath;
    @NotNull
    private String temporalFrontEnd;
    @NotNull
    private PrometheusConfig prometheusConfig;
    @NotNull
    private Long awaitTerminationTimeoutInSec;
    @NotNull
    private WorkerDynamicOptions workerDynamicOptions;

    private String temporalTaskQueue;

    // Optional Hadoop identity parameters, used when connecting to HBase
    private String hadoopUserName;
    private String hadoopLoginUser;

    // Authentication configuration
    @NotNull
    private String authPropertiesPath;

    @NotNull
    private String abPropertiesPath;

    private ActivityDefaultsConfig activityDefaults;
}
