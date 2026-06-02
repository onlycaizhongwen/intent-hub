package com.intenthub.infrastructure.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "intent-hub.model-service")
public record ModelServiceProperties(
        boolean enabled,
        String baseUrl,
        int timeoutMs
) {
    public ModelServiceProperties {
        timeoutMs = timeoutMs <= 0 ? 2000 : timeoutMs;
    }

    public boolean active() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }
}
