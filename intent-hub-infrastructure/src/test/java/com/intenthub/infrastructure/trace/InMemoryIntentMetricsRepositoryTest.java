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
        repository.recordLlmBudgetReconciliation(3);
        repository.recordLlmBudgetReconciliation(0);
        repository.recordLlmBudgetReconciliation(-1);
        repository.recordPermissionDenied("demo", "order-scene", "approve config version");
        repository.recordPermissionDenied("demo", "order-scene", "get config version");
        repository.recordAdminJwtAuthFailure("invalid admin jwt signature");
        repository.recordAdminJwksFetch();
        repository.recordAdminJwksFetch();
        repository.recordAdminJwksFetchFailure();
        repository.recordAdminJwksCacheHit();
        repository.recordAdminJwksStaleHit();

        MetricsSnapshot snapshot = repository.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(2);
        assertThat(snapshot.totalModelFallbacks()).isEqualTo(1);
        assertThat(snapshot.totalLlmFallbacks()).isEqualTo(1);
        assertThat(snapshot.totalLlmBudgetAttempts()).isEqualTo(2);
        assertThat(snapshot.totalLlmBudgetConsumed()).isEqualTo(2.0);
        assertThat(snapshot.totalLlmBudgetReconciliations()).isEqualTo(3);
        assertThat(snapshot.totalPermissionDenied()).isEqualTo(2);
        assertThat(snapshot.totalAdminJwtAuthFailures()).isEqualTo(1);
        assertThat(snapshot.totalAdminJwksFetches()).isEqualTo(2);
        assertThat(snapshot.totalAdminJwksFetchFailures()).isEqualTo(1);
        assertThat(snapshot.totalAdminJwksCacheHits()).isEqualTo(1);
        assertThat(snapshot.totalAdminJwksStaleHits()).isEqualTo(1);
        assertThat(snapshot.totalBadCases()).isEqualTo(2);
        assertThat(snapshot.p95LatencyMillis()).isEqualTo(18.0);
        assertThat(snapshot.p99LatencyMillis()).isEqualTo(18.0);
    }

    @Test
    void calculatesLatencyPercentilesFromRecentSamples() {
        InMemoryIntentMetricsRepository repository = new InMemoryIntentMetricsRepository();

        for (int index = 1; index <= 100; index++) {
            Envelope envelope = envelope("REQ-METRICS-PERCENTILE-" + index);
            repository.recordRecognition(envelope, result(envelope, List.of("RuleRecognitionPolicy")), index);
        }

        MetricsSnapshot snapshot = repository.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(100);
        assertThat(snapshot.averageLatencyMillis()).isEqualTo(50.5);
        assertThat(snapshot.maxLatencyMillis()).isEqualTo(100);
        assertThat(snapshot.p95LatencyMillis()).isEqualTo(95.0);
        assertThat(snapshot.p99LatencyMillis()).isEqualTo(99.0);
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
