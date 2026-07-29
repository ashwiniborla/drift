package com.flipkart.drift.persistence.dao;

import com.flipkart.drift.persistence.bootstrap.HbaseNamespaceConfig;
import lombok.Getter;

@Getter
public enum ConnectionType {
    HOT("ims_hot"),COLD("ims_cold"),ARCHIVAL("ims_archive"),AUDIT("ims_audit");

    ConnectionType(String namespace) {
        this.namespace = namespace;
    }

    private String namespace;

    /**
     * Applies namespace overrides from the Dropwizard config, replacing the compiled-in
     * defaults. Must be called once during application startup
     */
    public static void init(HbaseNamespaceConfig config) {
        if (config == null) {
            return;
        }
        applyOverride(HOT, config.getHot());
        applyOverride(COLD, config.getCold());
        applyOverride(ARCHIVAL, config.getArchival());
        applyOverride(AUDIT, config.getAudit());
    }

    private static void applyOverride(ConnectionType type, String namespace) {
        if (namespace != null && !namespace.isBlank()) {
            type.namespace = namespace;
        }
    }
}

