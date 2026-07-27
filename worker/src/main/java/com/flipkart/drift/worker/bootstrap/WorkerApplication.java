package com.flipkart.drift.worker.bootstrap;

import com.flipkart.drift.persistence.bootstrap.DriftEntityModule;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.worker.util.AuthNTokenGenerator;
import com.flipkart.drift.worker.config.DriftWorkerConfiguration;
import com.flipkart.drift.worker.resources.DriftWorkerResource;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.config.ConcurrentCompositeConfiguration;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicURLConfiguration;
import com.sun.net.httpserver.HttpServer;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;

import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Slf4j
public class WorkerApplication extends Application<DriftWorkerConfiguration> {
    public static void main(String[] args) throws Exception {
        new WorkerApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DriftWorkerConfiguration> bootstrap) {
        // Enable environment variable substitution in configuration YAML
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false) // false = don't throw on missing vars
                )
        );
    }

    @Override
    public void run(DriftWorkerConfiguration driftWorkerConfiguration, Environment environment) {
        ConnectionType.init(driftWorkerConfiguration.getHbaseNamespaceConfig());
        Scope metricsScope = setupMetrics(driftWorkerConfiguration.getPrometheusConfig());
        ConcurrentCompositeConfiguration compositeConfiguration = getConcurrentCompositeConfiguration(driftWorkerConfiguration);
        DynamicPropertyFactory.initWithConfigurationSource(compositeConfiguration);
        init(driftWorkerConfiguration, environment, metricsScope);

        // Initialize authentication token generator with pluggable TokenProvider
        if (!AuthNTokenGenerator.INSTANCE.isInitialized()) {
            AuthNTokenGenerator.INSTANCE.init();
        }
    }

    private Scope setupMetrics(com.flipkart.drift.worker.config.PrometheusConfig prometheusConfig) {
        try {
            PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            Scope scope = new RootScopeBuilder()
                    .reporter(new MicrometerClientStatsReporter(registry))
                    .reportEvery(com.uber.m3.util.Duration.ofSeconds(1));

            HttpServer metricsServer = HttpServer.create(new InetSocketAddress(prometheusConfig.getPort()), 0);
            metricsServer.createContext(prometheusConfig.getPath(), httpExchange -> {
                String response = registry.scrape();
                httpExchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            });
            metricsServer.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                metricsServer.stop(1);
            }));

            log.info("Metrics server started on port {}", prometheusConfig.getPort());
            return scope;
        } catch (Exception e) {
            log.error("Failed to start metrics server", e);
            throw new RuntimeException("Failed to setup metrics", e);
        }
    }

    private void init(DriftWorkerConfiguration driftWorkerConfiguration, Environment environment, Scope metricsScope) {
        Injector injector;
        injector = Guice.createInjector(new WorkerModule(driftWorkerConfiguration), new DriftEntityModule());
        // Register worker factory as a managed component for graceful shutdown
        environment.lifecycle().manage(new TemporalWorkerManaged(injector, driftWorkerConfiguration, metricsScope));
        environment.lifecycle().manage(injector.getInstance(RedisCacheInvalidator.class));
        environment.jersey().register(injector.getInstance(DriftWorkerResource.class));
    }

    private static ConcurrentCompositeConfiguration getConcurrentCompositeConfiguration(DriftWorkerConfiguration configuration) {
        DynamicURLConfiguration dynamicConfiguration = new DynamicURLConfiguration(
                50000,
                20000,
                false,
                configuration.getHbasePropertiesPath(),
                configuration.getLookupPropertiesPath(),
                configuration.getAuthPropertiesPath(),
                configuration.getAbPropertiesPath(),
                configuration.getWorkflowPropertiesPath()
        );
        ConcurrentCompositeConfiguration compositeConfiguration =
                new ConcurrentCompositeConfiguration();
        compositeConfiguration.addConfiguration(dynamicConfiguration);
        return compositeConfiguration;
    }
}
