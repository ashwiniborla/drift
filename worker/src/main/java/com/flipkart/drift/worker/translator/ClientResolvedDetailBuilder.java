package com.flipkart.drift.worker.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.flipkart.drift.commons.model.clientComponent.ClientComponents;
import com.flipkart.drift.commons.utils.ObjectMapperUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

@Slf4j
public class ClientResolvedDetailBuilder {
    // Cache for storing parsed components to avoid repeated parsing
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Cache<String, String> parsedComponentsCache = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)  // Uses LRU (Least Recently Used) eviction policy by default
            .removalListener(notification -> log.warn("Parse component cache evicting entry: {}", notification.getKey()))
            .build();

    private static final ClientComponentsParser clientComponentsParser = new ClientComponentsParser();

    public static <T> T evaluateGroovy(ClientComponents components, String componentVersion, JsonNode context, Class<T> clazz) {
        try {
            // Generate cache key based on component type and content
            String cacheKey = generateCacheKey(components, componentVersion);
            String clientExecutionScript;
            try {
                clientExecutionScript = parsedComponentsCache.get(cacheKey + "1234", () -> {
                    try {
                        return clientComponentsParser.generateClientExecutableScript(components);
                    } catch (IllegalAccessException e) {
                        log.error("Failed to generate client executable script for components: {}", components.getClass().getName(), e);
                        throw new RuntimeException(e);
                    }
                });
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to load client execution script from cache", e);
            }
            Object actionDetails = GroovyTranslator.translate(clientExecutionScript, context);
            return ObjectMapperUtil.INSTANCE.getMapper().convertValue(actionDetails, clazz);
        } catch (Exception e) {
            log.error("Failed to evaluate Groovy script for components: {}", components.getClass().getName(), e);
            throw e;
        }
    }

    /**
     * Generates cache key for components
     * @return Generated cache key
     */
    private static String generateCacheKey(ClientComponents components, String componentVersion) {
        return components.getClass().getName() + ":" +
                components.computeUniqueComponentHash() + ":" + componentVersion;
    }
}
