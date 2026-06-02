package com.intenthub.infrastructure.llm;

import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import com.intenthub.infrastructure.http.ExternalRestClients;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public class TongyiLlmAdapter implements LlmClientPort {
    private final RestClient restClient;
    private final ChatClient chatClient;
    private final LlmGovernanceProperties properties;
    private final IntentMetricsPort metricsPort;
    private final LlmBudgetAuditPort budgetAuditPort;

    public TongyiLlmAdapter(RestClient.Builder restClientBuilder, LlmGovernanceProperties properties, IntentMetricsPort metricsPort) {
        this(restClientBuilder, null, properties, metricsPort, new NoopLlmBudgetAuditPort());
    }

    public TongyiLlmAdapter(
            RestClient.Builder restClientBuilder,
            ChatClient.Builder chatClientBuilder,
            LlmGovernanceProperties properties,
            IntentMetricsPort metricsPort,
            LlmBudgetAuditPort budgetAuditPort
    ) {
        this(
                ExternalRestClients.build(restClientBuilder, properties.baseUrl(), properties.timeoutMs()),
                chatClientBuilder == null ? null : chatClientBuilder.build(),
                properties,
                metricsPort,
                budgetAuditPort
        );
    }

    TongyiLlmAdapter(RestClient restClient, LlmGovernanceProperties properties) {
        this(restClient, null, properties, new NoopIntentMetricsPort(), new NoopLlmBudgetAuditPort());
    }

    TongyiLlmAdapter(RestClient restClient, LlmGovernanceProperties properties, IntentMetricsPort metricsPort) {
        this(restClient, null, properties, metricsPort, new NoopLlmBudgetAuditPort());
    }

    TongyiLlmAdapter(RestClient restClient, ChatClient chatClient, LlmGovernanceProperties properties, IntentMetricsPort metricsPort) {
        this(restClient, chatClient, properties, metricsPort, new NoopLlmBudgetAuditPort());
    }

    TongyiLlmAdapter(RestClient restClient, ChatClient chatClient, LlmGovernanceProperties properties, IntentMetricsPort metricsPort, LlmBudgetAuditPort budgetAuditPort) {
        this.restClient = restClient;
        this.chatClient = chatClient;
        this.properties = properties;
        this.metricsPort = metricsPort == null ? new NoopIntentMetricsPort() : metricsPort;
        this.budgetAuditPort = budgetAuditPort == null ? new NoopLlmBudgetAuditPort() : budgetAuditPort;
    }

    @Override
    public Optional<RecognitionCandidate> recognize(String text, String tenantId, String sceneId, LlmPolicy policy) {
        if (!active(policy)) {
            return Optional.empty();
        }
        RuntimeException lastFailure = null;
        int attempts = Math.min(policy.maxRetries(), properties.maxRetries()) + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                metricsPort.recordLlmBudgetConsumption(1.0);
                budgetAuditPort.recordAttempt(tenantId, sceneId, policy.provider(), policy.model(), 1.0);
                LlmRecognitionResponse response = recognizeByProvider(text, sceneId, policy);
                return candidate(response);
            } catch (RuntimeException ex) {
                lastFailure = ex;
            }
        }
        throw lastFailure == null ? new IllegalStateException("LLM request failed") : lastFailure;
    }

    private boolean active(LlmPolicy policy) {
        return properties.active()
                && policy.enabled()
                && policy.dailyBudget() > 0.0
                && policy.timeoutMs() > 0;
    }

    private LlmRecognitionResponse recognizeByProvider(String text, String sceneId, LlmPolicy policy) {
        if (chatClient != null && "spring-ai-alibaba".equalsIgnoreCase(policy.provider())) {
            return chatClient.prompt()
                    .system("""
                            You are the controlled fallback recognizer for IntentHub.
                            Return only structured data matching this schema:
                            intentCode string, confidence number from 0.0 to 1.0,
                            slots object of string values, explanation string.
                            Do not execute business actions or request business data.
                            """)
                    .user("sceneId=%s\nmodel=%s\ntext=%s".formatted(sceneId, policy.model(), text))
                    .call()
                    .entity(LlmRecognitionResponse.class);
        }
        return restClient.post()
                .uri("/recognize")
                .body(new LlmRecognitionRequest(text, sceneId, policy.provider(), policy.model()))
                .retrieve()
                .body(LlmRecognitionResponse.class);
    }

    private Optional<RecognitionCandidate> candidate(LlmRecognitionResponse response) {
        if (response == null || response.intentCode() == null || response.intentCode().isBlank()) {
            return Optional.empty();
        }
        if (response.confidence() < properties.minConfidence()) {
            return Optional.empty();
        }
        return Optional.of(new RecognitionCandidate(
                response.intentCode(),
                response.confidence(),
                response.slots(),
                response.explanation() == null ? "llm hit" : response.explanation()
        ));
    }

    private static final class NoopIntentMetricsPort implements IntentMetricsPort {
        @Override
        public void recordRecognition(com.intenthub.domain.recognition.Envelope envelope,
                                      com.intenthub.domain.recognition.IntentResult result,
                                      long latencyMillis) {
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(0, 0, 0, 0, 0, 0.0, 0, 0.0, 0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        }
    }

    private static final class NoopLlmBudgetAuditPort implements LlmBudgetAuditPort {
        @Override
        public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
        }

        @Override
        public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
            return new LlmBudgetUsage(tenantId, sceneId, usageDate, 0, 0.0);
        }
    }
}
