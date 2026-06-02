package com.intenthub.domain.config;

public record LlmPolicy(
        boolean enabled,
        String provider,
        String model,
        int timeoutMs,
        int maxRetries,
        double dailyBudget,
        String fallbackDecision
) {
    public LlmPolicy {
        provider = provider == null || provider.isBlank() ? "none" : provider;
        model = model == null || model.isBlank() ? "none" : model;
        timeoutMs = Math.max(timeoutMs, 0);
        maxRetries = Math.max(maxRetries, 0);
        dailyBudget = Math.max(dailyBudget, 0.0);
        fallbackDecision = fallbackDecision == null || fallbackDecision.isBlank() ? "REJECTED" : fallbackDecision;
    }

    public static LlmPolicy disabled() {
        return new LlmPolicy(false, "none", "none", 0, 0, 0.0, "REJECTED");
    }
}
