package com.intenthub.infrastructure.model;

import com.intenthub.domain.config.ModelPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ModelClientAdapterTest {
    @Test
    void noopAdapterReturnsEmptyCandidate() {
        assertThat(new NoopModelClientAdapter().recognize("text", "scene")).isEmpty();
        assertThat(new NoopModelClientAdapter().healthy()).isFalse();
        assertThat(new NoopModelClientAdapter().healthDetails().healthy()).isFalse();
    }

    @Test
    void inactiveHttpAdapterReturnsEmptyCandidateWithoutCallingRemote() {
        ModelServiceProperties properties = new ModelServiceProperties(false, "", 2000);
        HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);

        assertThat(adapter.recognize("text", "scene")).isEmpty();
        assertThat(adapter.healthy()).isFalse();
    }

    @Test
    void httpAdapterChecksRemoteHealth() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://model.example.test/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"UP\",\"modelVersion\":\"example-v1\",\"threshold\":0.7}", MediaType.APPLICATION_JSON));
        ModelServiceProperties properties = new ModelServiceProperties(true, "https://model.example.test", 2000);
        HttpModelClientAdapter adapter = new HttpModelClientAdapter(builder.baseUrl(properties.baseUrl()).build(), properties);

        assertThat(adapter.healthDetails()).satisfies(health -> {
            assertThat(health.healthy()).isTrue();
            assertThat(health.modelVersion()).isEqualTo("example-v1");
            assertThat(health.threshold()).isEqualTo(0.7);
        });
        server.verify();
    }

    @Test
    void httpAdapterReturnsRemoteCandidate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://model.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"intentCode":"ORDER_QUERY","confidence":0.81,"slots":{},"explanation":"model hit"}
                        """, MediaType.APPLICATION_JSON));
        ModelServiceProperties properties = new ModelServiceProperties(true, "https://model.example.test", 2000);
        HttpModelClientAdapter adapter = new HttpModelClientAdapter(builder.baseUrl(properties.baseUrl()).build(), properties);

        assertThat(adapter.recognize("text", "scene"))
                .hasValueSatisfying(candidate -> {
                    assertThat(candidate.intentCode()).isEqualTo("ORDER_QUERY");
                    assertThat(candidate.confidence()).isEqualTo(0.81);
                    assertThat(candidate.explanation()).isEqualTo("model hit");
                });
        server.verify();
    }

    @Test
    void httpAdapterUsesSceneEndpointWhenModelPolicyOverridesGlobalBaseUrl() throws IOException {
        withLocalModelServer("""
                {"intentCode":"ORDER_QUERY","confidence":0.83,"slots":{},"explanation":"scene model hit"}
                """, baseUrl -> {
            ModelServiceProperties properties = new ModelServiceProperties(true, "https://global-model.example.test", 2000);
            HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);

            assertThat(adapter.recognize("text", "scene", new ModelPolicy(true, baseUrl, 1500, 0.70)))
                    .hasValueSatisfying(candidate -> {
                        assertThat(candidate.intentCode()).isEqualTo("ORDER_QUERY");
                        assertThat(candidate.confidence()).isEqualTo(0.83);
                        assertThat(candidate.explanation()).isEqualTo("scene model hit");
                    });
        });
    }

    @Test
    void httpAdapterCanUseSceneEndpointWithoutGlobalBaseUrl() throws IOException {
        withLocalModelServer("""
                {"intentCode":"ORDER_CANCEL","confidence":0.84,"slots":{},"explanation":"scene only hit"}
                """, baseUrl -> {
            ModelServiceProperties properties = new ModelServiceProperties(true, "", 2000);
            HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);

            assertThat(adapter.recognize("text", "scene", new ModelPolicy(true, baseUrl, 1500, 0.70)))
                    .hasValueSatisfying(candidate -> {
                        assertThat(candidate.intentCode()).isEqualTo("ORDER_CANCEL");
                        assertThat(candidate.confidence()).isEqualTo(0.84);
                    });
        });
    }

    @Test
    void httpAdapterReusesSceneEndpointClientByEndpointAndTimeout() throws IOException {
        withLocalModelServer("""
                {"intentCode":"ORDER_QUERY","confidence":0.85,"slots":{},"explanation":"cached scene hit"}
                """, baseUrl -> {
            ModelServiceProperties properties = new ModelServiceProperties(true, "", 2000);
            HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);
            ModelPolicy policy = new ModelPolicy(true, baseUrl, 1500, 0.70);

            adapter.recognize("first", "scene", policy);
            adapter.recognize("second", "scene", policy);

            assertThat(adapter.cachedClientCount()).isEqualTo(1);
        });
    }

    @Test
    void httpAdapterSendsBearerTokenFromSceneModelPolicyTokenRef() throws IOException {
        System.setProperty("INTENT_HUB_MODEL_TOKEN_TEST", "scene-secret");
        try {
            withLocalModelServer("""
                    {"intentCode":"ORDER_QUERY","confidence":0.85,"slots":{},"explanation":"authorized scene hit"}
                    """, exchange -> assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer scene-secret"), baseUrl -> {
                ModelServiceProperties properties = new ModelServiceProperties(true, "", 2000);
                HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);

                assertThat(adapter.recognize("text", "scene", new ModelPolicy(true, baseUrl, 1500, 0.70, "INTENT_HUB_MODEL_TOKEN_TEST")))
                        .hasValueSatisfying(candidate -> assertThat(candidate.intentCode()).isEqualTo("ORDER_QUERY"));
            });
        } finally {
            System.clearProperty("INTENT_HUB_MODEL_TOKEN_TEST");
        }
    }

    @Test
    void httpAdapterFailsClosedWithoutCallingRemoteWhenTokenRefCannotBeResolved() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        withLocalModelServer("""
                {"intentCode":"ORDER_QUERY","confidence":0.85,"slots":{},"explanation":"should not be called"}
                """, exchange -> requests.incrementAndGet(), baseUrl -> {
            ModelServiceProperties properties = new ModelServiceProperties(true, "", 2000);
            HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);

            assertThatThrownBy(() -> adapter.recognize("text", "scene", new ModelPolicy(true, baseUrl, 1500, 0.70, "INTENT_HUB_MODEL_TOKEN_MISSING_TEST")))
                    .isInstanceOf(com.intenthub.domain.recognition.policy.ModelServiceAuthenticationException.class)
                    .hasMessage("MODEL_FALLBACK:AUTH_MISSING_TOKEN");
            assertThat(requests).hasValue(0);
        });
    }

    @Test
    void httpAdapterWorksAgainstLocalHttpServerSmoke() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/recognize", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(new String(requestBody, StandardCharsets.UTF_8)).contains("order-scene");
            respond(exchange, """
                    {"intentCode":"ORDER_CANCEL","confidence":0.86,"slots":{"order_id":"A100"},"explanation":"local smoke hit"}
                    """);
        });
        server.start();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            ModelServiceProperties properties = new ModelServiceProperties(true, baseUrl, 2000);
            HttpModelClientAdapter adapter = new HttpModelClientAdapter(RestClient.builder(), properties);

            assertThat(adapter.recognize("cancel A100", "order-scene"))
                    .hasValueSatisfying(candidate -> {
                        assertThat(candidate.intentCode()).isEqualTo("ORDER_CANCEL");
                        assertThat(candidate.confidence()).isEqualTo(0.86);
                        assertThat(candidate.slots()).containsEntry("order_id", "A100");
                        assertThat(candidate.explanation()).isEqualTo("local smoke hit");
                    });
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void withLocalModelServer(String responseJson, LocalModelServerAssertion assertion) throws IOException {
        withLocalModelServer(responseJson, exchange -> {
        }, assertion);
    }

    private void withLocalModelServer(String responseJson, LocalModelServerExchangeAssertion exchangeAssertion, LocalModelServerAssertion assertion) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/recognize", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            exchangeAssertion.verify(exchange);
            respond(exchange, responseJson);
        });
        server.start();
        try {
            assertion.verify("http://localhost:" + server.getAddress().getPort());
        } finally {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface LocalModelServerAssertion {
        void verify(String baseUrl);
    }

    @FunctionalInterface
    private interface LocalModelServerExchangeAssertion {
        void verify(HttpExchange exchange);
    }
}
