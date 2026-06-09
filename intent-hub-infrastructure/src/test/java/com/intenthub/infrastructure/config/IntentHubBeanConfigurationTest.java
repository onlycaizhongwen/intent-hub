package com.intenthub.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import com.intenthub.infrastructure.security.ManagedConfigSecretProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntentHubBeanConfigurationTest {
    @TempDir
    Path tempDir;

    @Test
    void providesRestClientBuilderForHttpAdapters() {
        IntentHubBeanConfiguration configuration = new IntentHubBeanConfiguration();

        assertThat(configuration.restClientBuilder()).isInstanceOf(RestClient.Builder.class);
    }

    @Test
    void providesDefaultSecretReferenceResolver() {
        IntentHubBeanConfiguration configuration = new IntentHubBeanConfiguration();

        assertThat(configuration.secretRefResolver("", new ManagedConfigSecretProperties())
                .resolve("INTENT_HUB_SECRET_REF_RESOLVER_MISSING_TEST")).isEmpty();
    }

    @Test
    void providesFileSecretReferenceResolverWhenRootIsConfigured() throws Exception {
        Files.writeString(tempDir.resolve("MODEL_TOKEN"), "secret-from-file");
        IntentHubBeanConfiguration configuration = new IntentHubBeanConfiguration();

        assertThat(configuration.secretRefResolver(tempDir.toString(), new ManagedConfigSecretProperties()).resolve("MODEL_TOKEN"))
                .contains("secret-from-file");
    }

    @Test
    void providesManagedConfigSecretReferenceResolver() {
        ManagedConfigSecretProperties properties = new ManagedConfigSecretProperties();
        properties.setEnabled(true);
        properties.setRefs(Map.of("MODEL_TOKEN", "secret-from-managed-config"));
        IntentHubBeanConfiguration configuration = new IntentHubBeanConfiguration();

        assertThat(configuration.secretRefResolver("", properties).resolve("MODEL_TOKEN"))
                .contains("secret-from-managed-config");
    }

    @Test
    void keepsFileSecretResolverBeforeManagedConfigResolver() throws Exception {
        Files.writeString(tempDir.resolve("MODEL_TOKEN"), "secret-from-file");
        ManagedConfigSecretProperties properties = new ManagedConfigSecretProperties();
        properties.setEnabled(true);
        properties.setRefs(Map.of("MODEL_TOKEN", "secret-from-managed-config"));
        IntentHubBeanConfiguration configuration = new IntentHubBeanConfiguration();

        assertThat(configuration.secretRefResolver(tempDir.toString(), properties).resolve("MODEL_TOKEN"))
                .contains("secret-from-file");
    }
}
