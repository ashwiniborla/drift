package com.flipkart.drift.api.service;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Timer;
import com.flipkart.drift.api.exception.ApiException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.JedisSentinelPool;

import javax.ws.rs.core.Response;
import java.util.concurrent.*;

import static com.flipkart.drift.commons.utils.Constants.Workflow.ASYNC_AWAIT_CHANNEL;
import static com.flipkart.drift.commons.utils.MetricsRegistry.*;

@Slf4j
@Singleton
public class RedisPubSubService implements Managed {
    private final JedisSentinelPool jedisSentinelPool;

    @Getter
    private final ExecutorService redisThreadPool;

    @Inject
    public RedisPubSubService(JedisSentinelPool jedisSentinelPool) {
        this.jedisSentinelPool = jedisSentinelPool;
        this.redisThreadPool = new ThreadPoolExecutor(
                10, 50,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), // strong back-pressure
                new ThreadPoolExecutor.AbortPolicy() // Will throw RejectedExecutionException
        );
        publishGaugeMetrics();
    }

    private void publishGaugeMetrics() {
        // Monitor jedis pool metrics
        registerGauge(
                this.getClass(),
                (Gauge<Integer>) jedisSentinelPool::getNumActive,
                "jedis", "numActiveConnections"
        );
        registerGauge(
                this.getClass(),
                (Gauge<Integer>) jedisSentinelPool::getNumIdle,
                "jedis", "numIdleConnections"
        );
        registerGauge(
                this.getClass(),
                (Gauge<Integer>) jedisSentinelPool::getNumWaiters,
                "jedis", "numWaiters"
        );

        // Monitor thread pool metrics
        if (redisThreadPool instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) redisThreadPool;

            registerGauge(
                    this.getClass(),
                    (Gauge<Integer>) threadPoolExecutor::getActiveCount,
                    "threadpool", "activeThreads"
            );
            registerGauge(
                    this.getClass(),
                    (Gauge<Integer>) threadPoolExecutor::getPoolSize,
                    "threadpool", "poolSize"
            );
            registerGauge(
                    this.getClass(),
                    (Gauge<Long>) threadPoolExecutor::getCompletedTaskCount,
                    "threadpool", "completedTasks"
            );
            registerGauge(
                    this.getClass(),
                    (Gauge<Long>) threadPoolExecutor::getTaskCount,
                    "threadpool", "totalTasks"
            );
            registerGauge(
                    this.getClass(),
                    (Gauge<Integer>) threadPoolExecutor::getLargestPoolSize,
                    "threadpool", "largestPoolSize"
            );
        }
    }

    public void subscribeAndExecute(String workflowId, Callable<Void> onSubscribeAction, String action) {
        CompletableFuture<Void> redisFuture = new CompletableFuture<>();
        String channelName = ASYNC_AWAIT_CHANNEL + workflowId;
        JedisPubSub pubSub = createPubSubListener(redisFuture, onSubscribeAction, action);
        Future<?> redisTask = null;
        try {
            redisTask = submitRedisSubscriptionTask(channelName, pubSub, redisFuture);
            waitForRedisResponse(pubSub, redisFuture, channelName);
        } finally {
            if (redisTask != null) {
                redisTask.cancel(true);
            }
        }
    }

    private JedisPubSub createPubSubListener(CompletableFuture<Void> redisFuture, Callable<Void> onSubscribeAction, 
                                             String action) {
        return new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                log.info("Message received on channel {}:{}", channel, message);
                redisFuture.complete(null);
                safeUnsubscribe(this, "receiving message", channel);
            }

            @Override
            public void onSubscribe(String channel, int subscribedChannels) {
                log.info("Subscribed to channel: {}", channel);
                try (Timer.Context ignored = timerContext(this.getClass(), action, "latency")) {
                    onSubscribeAction.call();
                } catch (Exception e) {
                    markMeter(this.getClass(), "onSubscribeAction", "exception");
                    log.error("Exception during onSubscribe action for channel: {}", channel, e);
                    safeUnsubscribe(this, "onSubscribeAction error", channel);
                    redisFuture.completeExceptionally(e);
                }
            }
        };
    }

    private Future<?> submitRedisSubscriptionTask(String channelName, JedisPubSub pubSub, 
                                                 CompletableFuture<Void> redisFuture) {
        try {
            return redisThreadPool.submit(() -> {
                try (Jedis jedis = jedisSentinelPool.getResource()) {
                    jedis.subscribe(pubSub, channelName);
                } catch (Exception e) {
                    markMeter(this.getClass(), "redis", "exception");
                    log.error("Error subscribing to Redis channel: {}: {}", channelName, e.getMessage(), e);
                    safeUnsubscribe(pubSub, "Redis exception", channelName);
                    redisFuture.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            markMeter(this.getClass(), "threadPool", "rejected", "exception");
            log.error("Redis subscription task was rejected for channel: {}: {}", channelName, e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Redis subscription task was rejected");
        }
    }

    private void waitForRedisResponse(JedisPubSub pubSub, CompletableFuture<Void> redisFuture, String channelName) {
        try {
            redisFuture.get(50, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            markMeter(this.getClass(), "subscribe", "timeout");
            log.warn("Timeout waiting for Redis event on channel: {}. Unsubscribing and cancelling task.", channelName);
            safeUnsubscribe(pubSub, "timeout", channelName);
            throw new ApiException(Response.Status.REQUEST_TIMEOUT, "Timeout waiting for workflow response");
        } catch (InterruptedException e) {
            logAndMarkMeter(channelName, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof io.temporal.client.WorkflowNotFoundException) {
                throw (io.temporal.client.WorkflowNotFoundException) cause;
            }
            logAndMarkMeter(channelName, e);
        }
    }

    private void logAndMarkMeter(String channelName, Exception e) {
        markMeter(this.getClass(), "subscribe", e.getClass().getName());
        log.error("Error waiting for Redis event on channel: {}: {}", channelName, e.getMessage(), e);
        throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Error waiting for workflow response");
    }

    private void safeUnsubscribe(JedisPubSub pubSub, String context, String channel) {
        try {
            if (pubSub.isSubscribed())
                pubSub.unsubscribe();
        } catch (Exception ex) {
            log.warn("Unsubscribe failed after {} for channel: {}", context, channel, ex);
        }
    }

    @Override
    public void start() {
        log.info("RedisPubSubService started");
    }

    @Override
    public void stop() {
        shutdown();
        log.info("RedisPubSubService stopped");
    }

    public void shutdown() {
        redisThreadPool.shutdownNow();
    }
}




