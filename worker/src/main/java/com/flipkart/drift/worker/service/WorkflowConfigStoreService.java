package com.flipkart.drift.worker.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Singleton;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration.AbstractConfiguration;

import java.util.*;

/**
 * Service for managing global configuration metadata.
 * All configuration properties use the "global.*" prefix.
 * 
 * Uses Guava Cache with LRU eviction and auto-refresh callbacks.
 * This is a singleton - only one instance will be created and shared across all activities.
 */
@Slf4j
@Singleton
public class WorkflowConfigStoreService {
    private static final String GLOBAL_PREFIX = "global.";
    private static final int MAX_CACHE_SIZE = 10000;
    
    private final Cache<String, Object> configCache;
    
    // Track if cache has been initialized
    private volatile boolean initialized = false;
    
    /**
     * Constructor - sets up cache and configuration change callbacks
     */
    public WorkflowConfigStoreService() {
        this.configCache = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .removalListener(notification -> 
                    log.warn("Config cache evicting entry: {}", notification.getKey()))
                .build();
        
        initializeCache();
        setupConfigChangeCallbacks();
    }

    /**
     * Returns global metadata.
     * 
     * @return Global metadata map
     */
    public Map<String, Object> getEnumMapping() {
        if (!initialized) {
            initializeCache();
        }

        Map<String, Object> result = new HashMap<>();

        // Add all global keys, stripping the prefix
        configCache.asMap().forEach((key, value) -> {
            if (key.startsWith(GLOBAL_PREFIX)) {
                String keyWithoutPrefix = key.substring(GLOBAL_PREFIX.length());
                result.put(keyWithoutPrefix, value);
            }
        });

        log.debug("Returned {} metadata entries", result.size());
        return result;
    }

    /**
     * Initialize the cache by loading all configuration properties.
     */
    private void initializeCache() {
        synchronized (this) {
            if (initialized) {
                return;
            }
            
            log.info("Initializing configuration cache");
            refreshCache();
            initialized = true;
            log.info("Configuration cache initialized with {} entries", configCache.size());
        }
    }
    
    /**
     * Refresh the cache by reloading all configuration properties.
     */
    private void refreshCache() {
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        Iterator<String> keys = config.getKeys();
        
        Map<String, Object> newEntries = new HashMap<>();
        int count = 0;
        
        while (keys.hasNext()) {
            String key = keys.next();
            // Only cache keys with global prefix
            if (key.startsWith(GLOBAL_PREFIX)) {
                Object value = config.getProperty(key);
                if (value != null) {
                    newEntries.put(key, value);
                    count++;
                }
            }
        }
        
        // Replace entire cache atomically
        configCache.invalidateAll();
        configCache.putAll(newEntries);

        log.info("Cache refreshed with {} global properties", count);
    }
    
    /**
     * Setup callbacks to auto-refresh cache when configuration changes.
     */
    private void setupConfigChangeCallbacks() {
        // Monitor a reload trigger property
        DynamicStringProperty reloadTrigger = DynamicPropertyFactory.getInstance()
            .getStringProperty("version", "");
        
        reloadTrigger.addCallback(() -> {
            log.info("Configuration reload triggered by version change");
            refreshCache();
        });
    }
}