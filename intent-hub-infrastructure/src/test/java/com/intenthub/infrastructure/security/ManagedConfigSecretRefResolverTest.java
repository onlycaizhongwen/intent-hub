package com.intenthub.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedConfigSecretRefResolverTest {
    @Test
    void returnsEmptyWhenDisabled() {
        ManagedConfigSecretProperties properties = new ManagedConfigSecretProperties();
        properties.setEnabled(false);
        properties.setRefs(Map.of("MODEL_TOKEN", "secret-from-managed-config"));
        ManagedConfigSecretRefResolver resolver = new ManagedConfigSecretRefResolver(properties);

        assertThat(resolver.resolve("MODEL_TOKEN")).isEmpty();
    }

    @Test
    void resolvesConfiguredReferenceWhenEnabled() {
        ManagedConfigSecretProperties properties = new ManagedConfigSecretProperties();
        properties.setEnabled(true);
        properties.setRefs(Map.of("MODEL_TOKEN", "secret-from-managed-config"));
        ManagedConfigSecretRefResolver resolver = new ManagedConfigSecretRefResolver(properties);

        assertThat(resolver.resolve(" MODEL_TOKEN ")).contains("secret-from-managed-config");
    }

    @Test
    void ignoresBlankOrMissingReferences() {
        ManagedConfigSecretProperties properties = new ManagedConfigSecretProperties();
        properties.setEnabled(true);
        properties.setRefs(Map.of("EMPTY_TOKEN", "   "));
        ManagedConfigSecretRefResolver resolver = new ManagedConfigSecretRefResolver(properties);

        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("MISSING_TOKEN")).isEmpty();
        assertThat(resolver.resolve("EMPTY_TOKEN")).isEmpty();
    }
}
