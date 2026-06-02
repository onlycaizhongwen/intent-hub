package com.intenthub.infrastructure.trace;

import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseTrainingSample;
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

class BadCaseWorkflowRepositoryTest {
    @Test
    void annotatesClosesAndExportsMemoryBadCases() {
        InMemoryBadCaseRepository repository = new InMemoryBadCaseRepository();
        repository.recordIfNeeded(envelope(), rejectedResult());

        assertThat(repository.listBadCases(new BadCaseQuery("demo", "order-scene", "UNKNOWN", "OPEN", 10))).hasSize(1);
        assertThat(repository.annotate("TRACE-BAD-WF-001", "ORDER_QUERY", "人工修正", "reviewer").status()).isEqualTo("ANNOTATED");

        List<BadCaseTrainingSample> samples = repository.exportTrainingSamples(
                new BadCaseQuery("demo", "order-scene", null, "ANNOTATED", 10),
                true,
                "trainer"
        );

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).intentCode()).isEqualTo("ORDER_QUERY");
        assertThat(repository.listBadCases(new BadCaseQuery("demo", "order-scene", "ORDER_QUERY", "EXPORTED", 10))).hasSize(1);
        assertThat(repository.close("TRACE-BAD-WF-001", "训练集已生成", "reviewer").status()).isEqualTo("CLOSED");
        assertThat(repository.listBadCases(new BadCaseQuery("demo", "order-scene", "ORDER_QUERY", "CLOSED", 10))).hasSize(1);
    }

    private Envelope envelope() {
        return new Envelope(
                "demo",
                "app",
                "chat",
                InputType.TEXT,
                "查一下订单",
                "REQ-BAD-WF-001",
                "TRACE-BAD-WF-001",
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Map.of(),
                List.of()
        );
    }

    private IntentResult rejectedResult() {
        return new IntentResult(
                "TRACE-BAD-WF-001",
                "REQ-BAD-WF-001",
                "demo",
                "order-scene",
                "UNKNOWN",
                Decision.REJECTED,
                0.0,
                Map.of(),
                List.of("PRE_ROUTE:order-scene:v1", "POST_ROUTE:NONE"),
                "无法识别意图",
                DownstreamAction.none(),
                null
        );
    }
}
