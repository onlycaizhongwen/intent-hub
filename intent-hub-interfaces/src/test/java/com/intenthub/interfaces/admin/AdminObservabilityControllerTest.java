package com.intenthub.interfaces.admin;

import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.application.observability.ObservabilityAppService;
import com.intenthub.application.observability.ObservabilityQueryPort;
import com.intenthub.application.observability.RecognitionTraceRecord;
import com.intenthub.domain.recognition.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminObservabilityControllerTest {
    private AdminObservabilityController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminObservabilityController(new ObservabilityAppService(new InMemoryObservabilityPort()));
    }

    @Test
    void getsTraceByTraceIdThroughControllerContract() {
        RecognitionTraceRecord record = controller.getTrace("TRACE-OBS-001");

        assertThat(record.traceId()).isEqualTo("TRACE-OBS-001");
        assertThat(record.intentCode()).isEqualTo("ORDER_QUERY");
        assertThat(record.decision()).isEqualTo(Decision.SUCCESS);
        assertThat(record.recognitionPath()).contains("PRE_ROUTE:order-scene:v1");
    }

    @Test
    void listsBadCasesThroughControllerContract() {
        List<BadCaseRecord> records = controller.listBadCases("demo", "order-scene", "UNKNOWN", "OPEN", 10);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).traceId()).isEqualTo("TRACE-BAD-001");
        assertThat(records.get(0).decision()).isEqualTo(Decision.REJECTED);
    }

    private static final class InMemoryObservabilityPort implements ObservabilityQueryPort {
        @Override
        public Optional<RecognitionTraceRecord> findTraceByTraceId(String traceId) {
            if (!"TRACE-OBS-001".equals(traceId)) {
                return Optional.empty();
            }
            return Optional.of(new RecognitionTraceRecord(
                    traceId,
                    "REQ-OBS-001",
                    "demo",
                    "order-scene",
                    "TEXT",
                    Map.of("text", "查一下订单"),
                    "ORDER_QUERY",
                    Decision.SUCCESS,
                    0.92,
                    Map.of(),
                    List.of("PRE_ROUTE:order-scene:v1", "RULE:ORDER_QUERY", "POST_ROUTE:ORDER_QUERY_SYNC"),
                    "ORDER_QUERY_SYNC",
                    null,
                    Instant.parse("2026-06-01T00:00:00Z")
            ));
        }

        @Override
        public List<BadCaseRecord> listBadCases(BadCaseQuery query) {
            BadCaseRecord record = new BadCaseRecord(
                    "TRACE-BAD-001",
                    "REQ-BAD-001",
                    "demo",
                    "order-scene",
                    "UNKNOWN",
                    Decision.REJECTED,
                    0.0,
                    "无法识别意图",
                    Map.of("text", "讲个笑话"),
                    "OPEN",
                    Instant.parse("2026-06-01T00:00:01Z")
            );
            if ("demo".equals(query.tenantId())
                    && "order-scene".equals(query.sceneId())
                    && "UNKNOWN".equals(query.intentCode())
                    && "OPEN".equals(query.status())) {
                return List.of(record);
            }
            return List.of();
        }
    }
}
