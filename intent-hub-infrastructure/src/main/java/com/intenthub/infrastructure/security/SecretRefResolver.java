package com.intenthub.infrastructure.security;

import java.util.Optional;

public interface SecretRefResolver {
    Optional<String> resolve(String ref);
}
