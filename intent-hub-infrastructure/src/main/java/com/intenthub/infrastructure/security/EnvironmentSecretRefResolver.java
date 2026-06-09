package com.intenthub.infrastructure.security;

import java.util.Optional;

public class EnvironmentSecretRefResolver implements SecretRefResolver {
    @Override
    public Optional<String> resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String normalizedRef = ref.trim();
        String systemPropertyValue = System.getProperty(normalizedRef);
        if (systemPropertyValue != null && !systemPropertyValue.isBlank()) {
            return Optional.of(systemPropertyValue);
        }
        String environmentValue = System.getenv(normalizedRef);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return Optional.of(environmentValue);
        }
        return Optional.empty();
    }
}
