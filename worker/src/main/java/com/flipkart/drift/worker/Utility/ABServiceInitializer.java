package com.flipkart.drift.worker.Utility;

import com.flipkart.drift.sdk.spi.ab.ABTestingProvider;
import com.flipkart.drift.sdk.spi.ab.ABTestingProviderFactory;
import com.flipkart.drift.sdk.spi.ab.NoOpABTestingProvider;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Initializes the A/B testing system on bootstrap.
 * Uses pluggable ABTestingProvider via SPI.
 * This class is completely agnostic of specific provider implementations.
 */
@Slf4j
@Singleton
public class ABServiceInitializer {
    
    private volatile ABTestingProvider abTestingProvider;
    
    @Getter
    private volatile boolean initialized = false;
    
    @Inject
    public ABServiceInitializer() {
        log.info("Initializing A/B Testing system");
        this.abTestingProvider = ABTestingProviderFactory.getInstance();
        initializeABProvider();
    }
    
    private void initializeABProvider() {
        if (initialized) {
            log.debug("A/B Testing provider already initialized, skipping...");
            return;
        }
        
        try {
            log.info("Initializing A/B Testing provider: {}", abTestingProvider.getClass().getSimpleName());
            
            abTestingProvider.init();
            
            if (abTestingProvider.isInitialized()) {
                initialized = true;
                log.info("A/B Testing provider initialized successfully: {}", 
                        abTestingProvider.getClass().getSimpleName());
            } else {
                log.error("A/B Testing provider initialization failed: falling back to NoOpABTestingProvider");
                disableABTesting();
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize A/B Testing provider: {}, Falling back to NoOpABTestingProvider", e.getMessage(), e);
            disableABTesting();
        }
    }

    private void disableABTesting() {
        NoOpABTestingProvider noOpABTestingProvider = new NoOpABTestingProvider();
        noOpABTestingProvider.init();
        this.abTestingProvider = noOpABTestingProvider;
        this.initialized = true;
    }
    
    /**
     * Get the initialized A/B testing provider.
     */
    public ABTestingProvider getProvider() {
        return abTestingProvider;
    }
}
