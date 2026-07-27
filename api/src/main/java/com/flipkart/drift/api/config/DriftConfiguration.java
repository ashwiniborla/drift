package com.flipkart.drift.api.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.persistence.bootstrap.CacheMaxEntriesConfig;
import com.flipkart.drift.persistence.bootstrap.HbaseNamespaceConfig;
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
    private RedisConfiguration redisConfiguration;
    @NotNull
    private ExecutorServiceConfig cacheRefreshExecutorServiceConfig;
    @NotNull
    private StaticCacheRefreshConfig staticCacheRefreshConfig;
    @NotNull
    private CacheMaxEntriesConfig cacheMaxEntriesConfig;
    
    private HbaseNamespaceConfig hbaseNamespaceConfig;
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
}

