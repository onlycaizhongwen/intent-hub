package com.intenthub.infrastructure.llm;

import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TongyiLlmAdapterTest {
    @Test
    void inactiveGovernanceReturnsEmptyWithoutCallingRemote() {
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                RestClient.builder(),
                new LlmGovernanceProperties(false, "", 3000, 0, 0.0, 0.70),
                new RecordingMetricsPort()
        );

        assertThat(adapter.recognize("text", "demo", "scene", policy(10.0))).isEmpty();
    }

    @Test
    void zeroPolicyBudgetReturnsEmptyWithoutCallingRemote() {
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                RestClient.builder(),
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 10.0, 0.70),
                new RecordingMetricsPort()
        );

        assertThat(adapter.recognize("text", "demo", "scene", policy(0.0))).isEmpty();
    }

    @Test
    void returnsCandidateWhenGovernanceAndPolicyAllowSmallTrafficFallback() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://llm.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"intentCode":"ORDER_QUERY","confidence":0.78,"slots":{},"explanation":"llm hit"}
                        """, MediaType.APPLICATION_JSON));
        RecordingMetricsPort metrics = new RecordingMetricsPort();
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder.baseUrl("https://llm.example.test").build(),
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 10.0, 0.70),
                metrics
        );

        assertThat(adapter.recognize("text", "demo", "scene", policy(10.0)))
                .hasValueSatisfying(candidate -> {
                    assertThat(candidate.intentCode()).isEqualTo("ORDER_QUERY");
                    assertThat(candidate.confidence()).isEqualTo(0.78);
                    assertThat(candidate.explanation()).isEqualTo("llm hit");
                });
        assertThat(metrics.attempts.get()).isEqualTo(1);
        assertThat(metrics.consumed.sum()).isEqualTo(1.0);
        server.verify();
    }

    @Test
    void retriesWithinGovernanceLimitThenFailsClosedToPolicyLayer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://llm.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        server.expect(once(), requestTo("https://llm.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        RecordingMetricsPort metrics = new RecordingMetricsPort();
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder.baseUrl("https://llm.example.test").build(),
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 1, 10.0, 0.70),
                metrics
        );

        assertThatThrownBy(() -> adapter.recognize("text", "demo", "scene", policy(10.0)))
                .isInstanceOf(RuntimeException.class);
        assertThat(metrics.attempts.get()).isEqualTo(2);
        assertThat(metrics.consumed.sum()).isEqualTo(2.0);
        server.verify();
    }

    @Test
    void usesSpringAiChatClientWhenAlibabaProviderIsConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(LlmRecognitionResponse.class))
                .thenReturn(new LlmRecognitionResponse("ORDER_QUERY", 0.82, Map.of(), "spring ai hit"));
        RecordingMetricsPort metrics = new RecordingMetricsPort();
        RecordingBudgetAuditPort budgetAudit = new RecordingBudgetAuditPort();
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder.baseUrl("https://llm.example.test"),
                chatClientBuilder,
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 10.0, 0.70),
                metrics,
                budgetAudit
        );

        assertThat(adapter.recognize("text", "demo", "scene", policy(10.0)))
                .hasValueSatisfying(candidate -> {
                    assertThat(candidate.intentCode()).isEqualTo("ORDER_QUERY");
                    assertThat(candidate.confidence()).isEqualTo(0.82);
                    assertThat(candidate.explanation()).isEqualTo("spring ai hit");
                });
        assertThat(metrics.attempts.get()).isEqualTo(1);
        assertThat(budgetAudit.attempts.get()).isEqualTo(1);
        assertThat(budgetAudit.tenantId).isEqualTo("demo");
        assertThat(budgetAudit.sceneId).isEqualTo("scene");
        server.verify();
    }

    @Test
    void fallsBackToHttpContractWhenProviderIsNotSpringAiAlibaba() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://llm.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"intentCode":"ORDER_CANCEL","confidence":0.79,"slots":{},"explanation":"http hit"}
                        """, MediaType.APPLICATION_JSON));
        ChatClient chatClient = mock(ChatClient.class);
        RecordingMetricsPort metrics = new RecordingMetricsPort();
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder.baseUrl("https://llm.example.test").build(),
                chatClient,
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 10.0, 0.70),
                metrics
        );

        LlmPolicy httpPolicy = new LlmPolicy(true, "http-contract", "qwen-plus", 3000, 1, 10.0, "REJECTED");
        assertThat(adapter.recognize("text", "demo", "scene", httpPolicy))
                .hasValueSatisfying(candidate -> assertThat(candidate.intentCode()).isEqualTo("ORDER_CANCEL"));
        verifyNoInteractions(chatClient);
        server.verify();
    }

    @Test
    void exhaustedDailyBudgetReturnsEmptyWithoutRemoteAttempt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RecordingMetricsPort metrics = new RecordingMetricsPort();
        RecordingBudgetAuditPort budgetAudit = new RecordingBudgetAuditPort(1.0);
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder.baseUrl("https://llm.example.test"),
                null,
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 10.0, 0.70),
                metrics,
                budgetAudit
        );

        assertThat(adapter.recognize("text", "demo", "scene", policy(1.0))).isEmpty();
        assertThat(metrics.attempts.get()).isZero();
        assertThat(budgetAudit.attempts.get()).isZero();
        server.verify();
    }

    @Test
    void globalDailyBudgetAlsoLimitsPolicyBudget() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RecordingMetricsPort metrics = new RecordingMetricsPort();
        RecordingBudgetAuditPort budgetAudit = new RecordingBudgetAuditPort(5.0);
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder.baseUrl("https://llm.example.test"),
                null,
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 5.0, 0.70),
                metrics,
                budgetAudit
        );

        assertThat(adapter.recognize("text", "demo", "scene", policy(10.0))).isEmpty();
        assertThat(metrics.attempts.get()).isZero();
        assertThat(budgetAudit.attempts.get()).isZero();
        server.verify();
    }

    private LlmPolicy policy(double dailyBudget) {
        return new LlmPolicy(true, "spring-ai-alibaba", "qwen-plus", 3000, 1, dailyBudget, "REJECTED");
    }

    private static final class RecordingMetricsPort implements IntentMetricsPort {
        private final AtomicLong attempts = new AtomicLong();
        private final DoubleAdder consumed = new DoubleAdder();

        @Override
        public void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis) {
        }

        @Override
        public void recordLlmBudgetConsumption(double units) {
            attempts.incrementAndGet();
            consumed.add(units);
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(0, 0, 0, 0, attempts.get(), consumed.sum(), 0, 0.0, 0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        }
    }

    private static final class RecordingBudgetAuditPort implements LlmBudgetAuditPort {
        private final AtomicLong attempts = new AtomicLong();
        private final double consumedUnits;
        private String tenantId;
        private String sceneId;

        private RecordingBudgetAuditPort() {
            this(0.0);
        }

        private RecordingBudgetAuditPort(double consumedUnits) {
            this.consumedUnits = consumedUnits;
        }

        @Override
        public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
            this.tenantId = tenantId;
            this.sceneId = sceneId;
            attempts.incrementAndGet();
        }

        @Override
        public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
            return new LlmBudgetUsage(tenantId, sceneId, usageDate, attempts.get(), consumedUnits + attempts.get());
        }
    }
}
