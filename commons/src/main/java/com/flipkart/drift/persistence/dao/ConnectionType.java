package com.flipkart.drift.persistence.dao;

import lombok.Getter;

@Getter
public enum ConnectionType {
    HOT("ims_hot"),COLD("ims_cold"),ARCHIVAL("ims_archive"),AUDIT("ims_audit");

    ConnectionType(String namespace) {
        String envKey = "HBASE_NAMESPACE_" + this.name();
        String overrideNamespace = System.getenv(envKey);
        this.namespace = (overrideNamespace!=null && !overrideNamespace.isBlank()) ? overrideNamespace : namespace;
    }

    private final String namespace;
}
