package com.flipkart.drift.worker.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.commons.model.node.NodeRetryConfig;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.worker.config.ActivityDefaultsConfig;
import io.temporal.activity.ActivityOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityOptionsBuilderTest {

    private WorkflowNode node(Integer timeoutSeconds, NodeRetryConfig retryConfig) {
        WorkflowNode n = new WorkflowNode();
        n.setTimeoutSeconds(timeoutSeconds);
        n.setRetryConfig(retryConfig);
        return n;
    }

    private NodeRetryConfig retry(int maxAttempts, int initialInterval, int maxInterval, double backoff) {
        return new NodeRetryConfig(maxAttempts, initialInterval, maxInterval, backoff);
    }

    private NodeRetryConfig retryMinimal(int maxAttempts) {
        NodeRetryConfig r = new NodeRetryConfig();
        r.setMaxAttempts(maxAttempts);
        return r;
    }

    @Test
    void nodeLevelTimeoutWins() {
        ActivityDefaultsConfig defaults = new ActivityDefaultsConfig(1, 10);
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(defaults);
        ActivityOptions options = builder.build(node(45, null));
        assertEquals(Duration.ofSeconds(45), options.getStartToCloseTimeout());
        assertEquals(1, options.getRetryOptions().getMaximumAttempts());
    }

    @Test
    void nodeLevelRetryWins() {
        ActivityDefaultsConfig defaults = new ActivityDefaultsConfig(1, 10);
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(defaults);
        ActivityOptions options = builder.build(node(null, retryMinimal(3)));
        assertEquals(Duration.ofSeconds(10), options.getStartToCloseTimeout());
        assertEquals(3, options.getRetryOptions().getMaximumAttempts());
    }

    @Test
    void partialOverrideTimeoutOnly() {
        ActivityDefaultsConfig defaults = new ActivityDefaultsConfig(2, 10);
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(defaults);
        ActivityOptions options = builder.build(node(20, null));
        assertEquals(Duration.ofSeconds(20), options.getStartToCloseTimeout());
        assertEquals(2, options.getRetryOptions().getMaximumAttempts());
    }

    @Test
    void partialOverrideRetryOnly() {
        ActivityDefaultsConfig defaults = new ActivityDefaultsConfig(1, 30);
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(defaults);
        ActivityOptions options = builder.build(node(null, retryMinimal(5)));
        assertEquals(Duration.ofSeconds(30), options.getStartToCloseTimeout());
        assertEquals(5, options.getRetryOptions().getMaximumAttempts());
    }

    @Test
    void partialRetryConfigOnlyInitialInterval_usesPlatformMaxAttempts() throws Exception {
        ActivityDefaultsConfig defaults = new ActivityDefaultsConfig(3, 10);
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(defaults);
        NodeRetryConfig partial = new ObjectMapper()
                .readValue("{\"initialIntervalSeconds\":5}", NodeRetryConfig.class);
        assertNull(partial.getMaxAttempts());

        ActivityOptions options = builder.build(node(null, partial));

        assertEquals(3, options.getRetryOptions().getMaximumAttempts());
        assertEquals(Duration.ofSeconds(5), options.getRetryOptions().getInitialInterval());
        assertEquals(Duration.ofSeconds(20), options.getRetryOptions().getMaximumInterval());
        assertEquals(2.0, options.getRetryOptions().getBackoffCoefficient(), 0.001);
    }

    @Test
    void allDefaultsNoNodeConfig() {
        // ActivityDefaultsConfig field defaults match the previously hardcoded values (10s, 1 attempt)
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(new ActivityDefaultsConfig());
        ActivityOptions options = builder.build(node(null, null));
        assertEquals(Duration.ofSeconds(10), options.getStartToCloseTimeout());
        assertEquals(1, options.getRetryOptions().getMaximumAttempts());
    }

    @Test
    void yamlDefaultsOnlyNoNodeConfig() {
        ActivityDefaultsConfig defaults = new ActivityDefaultsConfig(2, 30);
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(defaults);
        ActivityOptions options = builder.build(node(null, null));
        assertEquals(Duration.ofSeconds(30), options.getStartToCloseTimeout());
        assertEquals(2, options.getRetryOptions().getMaximumAttempts());
    }

    @Test
    void fullNodeOverride() {
        ActivityDefaultsConfig defaults = new ActivityDefaultsConfig(1, 10);
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(defaults);
        NodeRetryConfig retryConfig = retry(5, 3, 60, 1.5);
        ActivityOptions options = builder.build(node(60, retryConfig));
        assertEquals(Duration.ofSeconds(60), options.getStartToCloseTimeout());
        assertEquals(5,   options.getRetryOptions().getMaximumAttempts());
        assertEquals(Duration.ofSeconds(3),  options.getRetryOptions().getInitialInterval());
        assertEquals(Duration.ofSeconds(60), options.getRetryOptions().getMaximumInterval());
        assertEquals(1.5, options.getRetryOptions().getBackoffCoefficient(), 0.001);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(new ActivityDefaultsConfig(1, 10));
        assertThrows(IllegalArgumentException.class, () -> builder.build(node(0, null)));
        assertThrows(IllegalArgumentException.class, () -> builder.build(node(-5, null)));
    }

    @Test
    void rejectsNonPositiveMaxAttempts() {
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(new ActivityDefaultsConfig(1, 10));
        assertThrows(IllegalArgumentException.class, () -> builder.build(node(null, retryMinimal(0))));
        assertThrows(IllegalArgumentException.class, () -> builder.build(node(null, retryMinimal(-1))));
    }

    @Test
    void rejectsNonPositiveIntervals() {
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(new ActivityDefaultsConfig(1, 10));
        NodeRetryConfig badInitial = retry(2, 0, 20, 2.0);
        NodeRetryConfig badMax = retry(2, 1, 0, 2.0);
        assertThrows(IllegalArgumentException.class, () -> builder.build(node(null, badInitial)));
        assertThrows(IllegalArgumentException.class, () -> builder.build(node(null, badMax)));
    }

    @Test
    void rejectsBackoffBelowOne() {
        ActivityOptionsBuilder builder = new ActivityOptionsBuilder(new ActivityDefaultsConfig(1, 10));
        assertThrows(IllegalArgumentException.class, () -> builder.build(node(null, retry(2, 1, 20, 0.5))));
    }
}
