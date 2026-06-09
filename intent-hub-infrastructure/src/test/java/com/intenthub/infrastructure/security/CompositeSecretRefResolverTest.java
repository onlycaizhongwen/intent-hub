package com.intenthub.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeSecretRefResolverTest {
    @Test
    void resolvesByConfiguredOrder() {
        CompositeSecretRefResolver resolver = new CompositeSecretRefResolver(List.of(
                ref -> Optional.empty(),
                ref -> Optional.of("secret-from-second"),
                ref -> Optional.of("secret-from-third")
        ));

        assertThat(resolver.resolve("TOKEN_REF")).contains("secret-from-second");
    }

    @Test
    void returnsEmptyWhenAllResolversMiss() {
        CompositeSecretRefResolver resolver = new CompositeSecretRefResolver(Arrays.asList(
                ref -> Optional.empty(),
                null
        ));

        assertThat(resolver.resolve("TOKEN_REF")).isEmpty();
    }
}
