package com.intenthub.interfaces.admin;

import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsAppService;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMetricsControllerTest {
    @Test
    void exposesMetricsSnapshotThroughControllerContract() {
        AdminMetricsController controller = new AdminMetricsController(new MetricsAppService(new FixedMetricsPort()));

        MetricsSnapshot snapshot = controller.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(3);
        assertThat(snapshot.totalBadCases()).isEqualTo(1);
        assertThat(snapshot.decisions()).containsEntry("SUCCESS", 2L);
    }

    @Test
    void exposesPrometheusTextThroughControllerContract() {
        AdminMetricsController controller = new AdminMetricsController(new MetricsAppService(new FixedMetricsPort()));

        String text = controller.prometheus();

        assertThat(text).contains("intent_hub_requests_total 3");
        assertThat(text).contains("intent_hub_decisions_total{decision=\"SUCCESS\"} 2");
        assertThat(text).contains("intent_hub_intents_total{intent=\"ORDER_QUERY\"} 2");
    }

    private static final class FixedMetricsPort implements IntentMetricsPort {
        @Override
        public void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis) {
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(
                    3,
                    1,
                    0,
                    27,
                    9.0,
                    15,
                    Map.of("SUCCESS", 2L, "REJECTED", 1L),
                    Map.of("ORDER_QUERY", 2L, "UNKNOWN", 1L),
                    Map.of("order-scene", 3L),
                    Instant.parse("2026-06-02T00:00:00Z"),
                    Instant.parse("2026-06-02T00:00:01Z")
            );
        }
    }
}
