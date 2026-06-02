package com.intenthub.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "intent-hub.llm")
public record LlmGovernanceProperties(
        boolean enabled,
        String baseUrl,
        int timeoutMs,
        int maxRetries,
        double dailyBudget,
        double minConfidence
) {
    public LlmGovernanceProperties {
        baseUrl = baseUrl == null ? "" : baseUrl;
        timeoutMs = timeoutMs <= 0 ? 3000 : timeoutMs;
        maxRetries = Math.max(maxRetries, 0);
        dailyBudget = Math.max(dailyBudget, 0.0);
        minConfidence = minConfidence <= 0.0 ? 0.70 : minConfidence;
    }

    public boolean active() {
        return enabled && !baseUrl.isBlank() && dailyBudget > 0.0;
    }
}
