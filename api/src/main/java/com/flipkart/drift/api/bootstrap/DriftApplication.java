package com.flipkart.drift.api.bootstrap;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.exception.mapper.ApiExceptionMapper;
import com.flipkart.drift.api.filters.RequestFilter;
import com.flipkart.drift.api.filters.ResponseFilter;
import com.flipkart.drift.api.resources.WorkflowResource;
import com.flipkart.drift.api.resources.NodeDefinitionResource;
import com.flipkart.drift.api.resources.WorkflowDefinitionResource;
import com.flipkart.drift.api.module.WorkflowClientModule;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.config.*;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;


import java.util.TimeZone;

public class DriftApplication extends Application<DriftConfiguration> {

    private MetricRegistry metricRegistry;

    public static void main(String[] args) throws Exception {
        new DriftApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DriftConfiguration> bootstrap) {
        // Enable environment variable substitution in configuration YAML
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false) // false = don't throw on missing vars
                )
        );
        
        metricRegistry = bootstrap.getMetricRegistry();
        ObjectMapper objectMapper = bootstrap.getObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setTimeZone(TimeZone.getDefault());
        objectMapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

    }

    @Override
    public void run(DriftConfiguration configuration, Environment environment) {
        ConnectionType.init(configuration.getHbaseNamespaceConfig());
        // Initialize DynamicPropertyFactory with configuration source
        ConcurrentCompositeConfiguration compositeConfiguration = getConcurrentCompositeConfiguration(configuration);
        DynamicPropertyFactory.initWithConfigurationSource(compositeConfiguration);
        // Create Guice injector after properties are loaded
        Injector injector = Guice.createInjector(new WorkflowClientModule(configuration, environment, metricRegistry));
        environment.jersey().register(injector.getInstance(WorkflowResource.class));
        environment.jersey().register(injector.getInstance(NodeDefinitionResource.class));
        environment.jersey().register(injector.getInstance(WorkflowDefinitionResource.class));
        environment.jersey().register(injector.getInstance(RequestFilter.class));
        environment.jersey().register(injector.getInstance(ResponseFilter.class));
        environment.jersey().register(injector.getInstance(ApiExceptionMapper.class));
        final JmxReporter reporter = JmxReporter.forRegistry(metricRegistry).build();
        reporter.start();
    }

    private static ConcurrentCompositeConfiguration getConcurrentCompositeConfiguration(DriftConfiguration configuration) {
        DynamicURLConfiguration dynamicConfiguration = new DynamicURLConfiguration(
                50000,
                20000,
                false,
                configuration.getHbasePropertiesPath()
        );
        ConcurrentCompositeConfiguration compositeConfiguration =
                new ConcurrentCompositeConfiguration();
        compositeConfiguration.addConfiguration(dynamicConfiguration);
        return compositeConfiguration;
    }
}

