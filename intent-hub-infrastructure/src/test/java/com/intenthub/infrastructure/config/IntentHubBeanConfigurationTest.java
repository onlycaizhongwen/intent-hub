package com.intenthub.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class IntentHubBeanConfigurationTest {
    @Test
    void providesRestClientBuilderForHttpAdapters() {
        IntentHubBeanConfiguration configuration = new IntentHubBeanConfiguration();

        assertThat(configuration.restClientBuilder()).isInstanceOf(RestClient.Builder.class);
    }
}
