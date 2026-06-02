package com.intenthub.infrastructure.trace;

import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.InputType;
import com.intenthub.domain.recognition.IntentResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIntentMetricsRepositoryTest {
    @Test
    void countsModelAndLlmFallbackClosuresSeparately() {
        InMemoryIntentMetricsRepository repository = new InMemoryIntentMetricsRepository();
        Envelope envelope = envelope("REQ-METRICS-FALLBACK");

        repository.recordRecognition(envelope, result(envelope, List.of(
                "PRE_ROUTE:order-scene:v1",
                "MODEL_FALLBACK:CLOSED",
                "POST_ROUTE:NONE"
        )), 12);
        repository.recordRecognition(envelope, result(envelope, List.of(
                "PRE_ROUTE:order-scene:v1",
                "LlmRecognizePolicy",
                "LLM_FALLBACK:REJECTED",
                "POST_ROUTE:NONE"
        )), 18);
        repository.recordLlmBudgetConsumption(1.0);
        repository.recordLlmBudgetConsumption(1.0);

        MetricsSnapshot snapshot = repository.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(2);
        assertThat(snapshot.totalModelFallbacks()).isEqualTo(1);
        assertThat(snapshot.totalLlmFallbacks()).isEqualTo(1);
        assertThat(snapshot.totalLlmBudgetAttempts()).isEqualTo(2);
        assertThat(snapshot.totalLlmBudgetConsumed()).isEqualTo(2.0);
        assertThat(snapshot.totalBadCases()).isEqualTo(2);
    }

    private Envelope envelope(String requestId) {
        return new Envelope(
                "demo",
                "app",
                "chat",
                InputType.TEXT,
                "text",
                requestId,
                "TRACE-" + requestId,
                null,
                Instant.parse("2026-06-02T00:00:00Z"),
                Map.of(),
                List.of()
        );
    }

    private IntentResult result(Envelope envelope, List<String> path) {
        return new IntentResult(
                envelope.traceId(),
                envelope.requestId(),
                envelope.tenantId(),
                "order-scene",
                "UNKNOWN",
                Decision.REJECTED,
                0.0,
                Map.of(),
                path,
                "未识别到可执行意图",
                DownstreamAction.none(),
                null
        );
    }
}
