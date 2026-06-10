package com.intenthub.infrastructure.config;

import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryConfigGovernanceRepositoryTest {
    @Test
    void recordsPermissionDeniedMetricsWhenAuditEventIsPermissionDenied() {
        RecordingMetricsPort metricsPort = new RecordingMetricsPort();
        InMemoryConfigGovernanceRepository repository = new InMemoryConfigGovernanceRepository(metricsPort);

        repository.record("demo", "order-scene", "unknown", "CONFIG_PERMISSION_DENIED", "CONFIG_PERMISSION", "order-scene", Map.of(
                "action", "get config version",
                "requiredRole", "CONFIG_VIEWER"
        ));

        assertThat(metricsPort.permissionDenied.get()).isEqualTo(1);
        assertThat(repository.list("demo", "order-scene", "CONFIG_PERMISSION", "order-scene", 10))
                .extracting("action")
                .containsExactly("CONFIG_PERMISSION_DENIED");
    }

    private static final class RecordingMetricsPort implements IntentMetricsPort {
        private final AtomicLong permissionDenied = new AtomicLong();

        @Override
        public void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis) {
        }

        @Override
        public void recordPermissionDenied(String tenantId, String sceneId, String action) {
            permissionDenied.incrementAndGet();
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(0, 0, 0, 0, 0, 0.0, 0, permissionDenied.get(), 0, 0.0, 0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        }
    }
}
