package com.intenthub.interfaces.security;

public interface AdminJwksMetricsRecorder {
    AdminJwksMetricsRecorder NOOP = new AdminJwksMetricsRecorder() {
    };

    default void recordFetch() {
    }

    default void recordFetchFailure() {
    }

    default void recordCacheHit() {
    }

    default void recordStaleHit() {
    }
}
