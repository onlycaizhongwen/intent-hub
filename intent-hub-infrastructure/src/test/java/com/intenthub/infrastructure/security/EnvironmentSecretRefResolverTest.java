package com.intenthub.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentSecretRefResolverTest {
    @Test
    void resolvesSystemPropertyBeforeEnvironment() {
        System.setProperty("INTENT_HUB_SECRET_RESOLVER_TEST", "secret-from-property");
        try {
            EnvironmentSecretRefResolver resolver = new EnvironmentSecretRefResolver();

            assertThat(resolver.resolve("INTENT_HUB_SECRET_RESOLVER_TEST"))
                    .contains("secret-from-property");
        } finally {
            System.clearProperty("INTENT_HUB_SECRET_RESOLVER_TEST");
        }
    }

    @Test
    void returnsEmptyForBlankOrMissingReference() {
        EnvironmentSecretRefResolver resolver = new EnvironmentSecretRefResolver();

        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("INTENT_HUB_SECRET_RESOLVER_MISSING_TEST")).isEmpty();
    }
}
