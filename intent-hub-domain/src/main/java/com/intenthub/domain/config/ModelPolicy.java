package com.intenthub.domain.config;

public record ModelPolicy(
        boolean enabled,
        String endpoint,
        int timeoutMs,
        double minConfidence
) {
    public ModelPolicy {
        endpoint = endpoint == null ? "" : endpoint;
        timeoutMs = Math.max(timeoutMs, 0);
        minConfidence = Math.max(0.0, Math.min(1.0, minConfidence));
    }

    public static ModelPolicy enabledByDefault() {
        return new ModelPolicy(true, "", 0, 0.0);
    }

    public static ModelPolicy disabled() {
        return new ModelPolicy(false, "", 0, 0.0);
    }
}
