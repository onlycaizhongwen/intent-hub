package com.intenthub.interfaces.admin;

import com.intenthub.application.observability.BadCaseActionResult;
import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.application.observability.BadCaseTrainingSample;
import com.intenthub.application.observability.BadCaseWorkflowAppService;
import com.intenthub.application.observability.BadCaseWorkflowPort;
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
        controller = new AdminObservabilityController(
                new ObservabilityAppService(new InMemoryObservabilityPort()),
                new BadCaseWorkflowAppService(new InMemoryBadCaseWorkflowPort())
        );
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

    @Test
    void annotatesBadCaseThroughControllerContract() {
        BadCaseActionResult result = controller.annotateBadCase(
                "TRACE-BAD-001",
                new BadCaseAnnotationRequest("ORDER_QUERY", "人工修正为订单查询", "reviewer")
        );

        assertThat(result.traceId()).isEqualTo("TRACE-BAD-001");
        assertThat(result.status()).isEqualTo("ANNOTATED");
        assertThat(result.actor()).isEqualTo("reviewer");
    }

    @Test
    void closesBadCaseThroughControllerContract() {
        BadCaseActionResult result = controller.closeBadCase(
                "TRACE-BAD-001",
                new BadCaseActionRequest("已处理", "reviewer")
        );

        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.note()).isEqualTo("已处理");
    }

    @Test
    void exportsBadCasesThroughControllerContract() {
        List<BadCaseTrainingSample> samples = controller.exportBadCases("demo", "order-scene", "ANNOTATED", 10, true, "trainer");

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).traceId()).isEqualTo("TRACE-BAD-001");
        assertThat(samples.get(0).intentCode()).isEqualTo("ORDER_QUERY");
        assertThat(samples.get(0).text()).isEqualTo("查一下订单");
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

    private static final class InMemoryBadCaseWorkflowPort implements BadCaseWorkflowPort {
        @Override
        public BadCaseActionResult annotate(String traceId, String correctedIntentCode, String note, String actor) {
            return new BadCaseActionResult(traceId, "ANNOTATED", actor, note, Instant.parse("2026-06-01T00:00:02Z"));
        }

        @Override
        public BadCaseActionResult close(String traceId, String note, String actor) {
            return new BadCaseActionResult(traceId, "CLOSED", actor, note, Instant.parse("2026-06-01T00:00:03Z"));
        }

        @Override
        public List<BadCaseTrainingSample> exportTrainingSamples(BadCaseQuery query, boolean markExported, String actor) {
            if (!"demo".equals(query.tenantId()) || !"order-scene".equals(query.sceneId()) || !"ANNOTATED".equals(query.status())) {
                return List.of();
            }
            return List.of(new BadCaseTrainingSample(
                    "TRACE-BAD-001",
                    "REQ-BAD-001",
                    "demo",
                    "order-scene",
                    "查一下订单",
                    "ORDER_QUERY",
                    "REJECTED",
                    0.0,
                    "人工修正为订单查询",
                    markExported ? "EXPORTED" : "ANNOTATED",
                    Map.of("source", "app"),
                    Instant.parse("2026-06-01T00:00:01Z")
            ));
        }
    }
}
