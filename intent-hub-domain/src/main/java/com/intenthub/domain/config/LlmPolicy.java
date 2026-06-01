package com.intenthub.domain.config;

public record LlmPolicy(
        boolean enabled,
        String provider,
        String model,
        int timeoutMs,
        int maxRetries,
        String fallbackDecision
) {
    public static LlmPolicy disabled() {
        return new LlmPolicy(false, "none", "none", 0, 0, "REJECTED");
    }
}
