package com.flipkart.drift.worker.temporal;

import com.flipkart.drift.commons.model.node.NodeRetryConfig;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.worker.config.ActivityDefaultsConfig;
import io.temporal.activity.ActivityOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void allDefaultsNoNodeConfig() {
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
}
