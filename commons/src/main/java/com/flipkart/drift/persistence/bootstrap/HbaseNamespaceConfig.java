package com.flipkart.drift.persistence.bootstrap;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HbaseNamespaceConfig {
    private String hot;
    private String cold;
    private String archival;
    private String audit;
}
