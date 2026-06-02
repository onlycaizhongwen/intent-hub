package com.intenthub.infrastructure.llm;

import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import org.springframework.web.client.RestClient;

import java.util.Optional;

public class TongyiLlmAdapter implements LlmClientPort {
    private final RestClient restClient;
    private final LlmGovernanceProperties properties;

    public TongyiLlmAdapter(RestClient.Builder restClientBuilder, LlmGovernanceProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public Optional<RecognitionCandidate> recognize(String text, String sceneId, LlmPolicy policy) {
        if (!active(policy)) {
            return Optional.empty();
        }
        RuntimeException lastFailure = null;
        int attempts = Math.min(policy.maxRetries(), properties.maxRetries()) + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                LlmRecognitionResponse response = restClient.post()
                        .uri("/recognize")
                        .body(new LlmRecognitionRequest(text, sceneId, policy.provider(), policy.model()))
                        .retrieve()
                        .body(LlmRecognitionResponse.class);
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
}
