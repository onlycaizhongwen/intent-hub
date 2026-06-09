package com.intenthub.infrastructure.security;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CompositeSecretRefResolver implements SecretRefResolver {
    private final List<SecretRefResolver> resolvers;

    public CompositeSecretRefResolver(List<SecretRefResolver> resolvers) {
        this.resolvers = resolvers == null
                ? List.of()
                : resolvers.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<String> resolve(String ref) {
        for (SecretRefResolver resolver : resolvers) {
            Optional<String> value = resolver.resolve(ref);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
}
