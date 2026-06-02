package com.intenthub.infrastructure.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ModelClientAdapterTest {
    @Test
    void noopAdapterReturnsEmptyCandidate() {
        assertThat(new NoopModelClientAdapter().recognize("text", "scene")).isEmpty();
    }

    @Test
    void inactiveHttpAdapterReturnsEmptyCandidateWithoutCallingRemote() {
        ModelServiceProperties properties = new ModelServiceProperties(false, "", 2000);
        HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);

        assertThat(adapter.recognize("text", "scene")).isEmpty();
    }
}
