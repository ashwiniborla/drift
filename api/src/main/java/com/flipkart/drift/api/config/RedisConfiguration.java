package com.flipkart.drift.api.config;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RedisConfiguration {
    private String sentinels;
    private String password;
    private String master;
    private String prefix;
    private int maxTotal;
    private int maxIdle;
    private int minIdle;
    private int maxWaitMillis;
    private boolean testOnBorrow;
    private boolean blockWhenExhausted;
    private int database;
}




