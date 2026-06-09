package com.intenthub.infrastructure.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ManagedConfigSecretRefResolver implements SecretRefResolver {
    private final boolean enabled;
    private final Map<String, String> refs;

    public ManagedConfigSecretRefResolver(ManagedConfigSecretProperties properties) {
        this.enabled = properties != null && properties.isEnabled();
        this.refs = properties == null ? Map.of() : new LinkedHashMap<>(properties.getRefs());
    }

    @Override
    public Optional<String> resolve(String ref) {
        if (!enabled || ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String value = refs.get(ref.trim());
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
