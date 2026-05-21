package com.flipkart.drift.worker.temporal;

public final class ActivityOptionsBuilderHolder {

    private static volatile ActivityOptionsBuilder INSTANCE = null;

    private ActivityOptionsBuilderHolder() {}

    public static void init(ActivityOptionsBuilder builder) {
        INSTANCE = builder;
    }

    public static ActivityOptionsBuilder get() {
        ActivityOptionsBuilder instance = INSTANCE;
        if (instance == null) {
            throw new IllegalStateException(
                    "ActivityOptionsBuilderHolder not initialised — " +
                    "call init() before starting the Temporal worker."
            );
        }
        return instance;
    }
}
