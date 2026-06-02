package com.intenthub.interfaces.web;

import com.intenthub.application.RecognizeAppService;
import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.config.IntentRule;
import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.InputType;
import com.intenthub.domain.recognition.IntentResult;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import com.intenthub.domain.recognition.policy.ModelClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecognizeControllerTest {
    private RecognizeController controller;

    @BeforeEach
    void setUp() {
        controller = new RecognizeController(new RecognizeAppService(
                this::sceneConfig,
                (envelope, result) -> {
                },
                (envelope, result) -> {
                },
                (envelope, action) -> envelope.tenantId() + "|" + envelope.requestId() + "|" + action.actionCode(),
                new DisabledLlmClient(),
                new NoopMetricsPort(),
                new DisabledModelClient()
        ));
    }

    @Test
    void mapsRequestToEnvelopeAndReturnsRecognizedResult() {
        IntentResult result = controller.recognize(request("REQ-P1-HTTP-001", "查一下订单", "TRACE-HTTP-001"));

        assertThat(result.traceId()).isEqualTo("TRACE-HTTP-001");
        assertThat(result.requestId()).isEqualTo("REQ-P1-HTTP-001");
        assertThat(result.tenantId()).isEqualTo("demo");
        assertThat(result.intentCode()).isEqualTo("ORDER_QUERY");
        assertThat(result.decision()).isEqualTo(Decision.SUCCESS);
        assertThat(result.downstreamAction().actionCode()).isEqualTo("ORDER_QUERY_SYNC");
    }

    @Test
    void createsTraceIdWhenRequestTraceIdIsBlank() {
        IntentResult result = controller.recognize(request("REQ-P1-HTTP-TRACE", "查一下订单", " "));

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.traceId()).isNotEqualTo(" ");
    }

    @Test
    void clarifiesMissingOrderIdThroughControllerContract() {
        IntentResult result = controller.recognize(request("REQ-P1-HTTP-003", "帮我取消订单", null));

        assertThat(result.intentCode()).isEqualTo("ORDER_CANCEL");
        assertThat(result.decision()).isEqualTo(Decision.CLARIFY);
        assertThat(result.slots()).isEmpty();
        assertThat(result.idempotencyKey()).isNull();
    }

    @Test
    void keepsAsyncCancelIdempotencyStableThroughControllerContract() {
        RecognizeRequest request = request("REQ-P1-HTTP-002", "取消订单 O20260601001", null);

        IntentResult first = controller.recognize(request);
        IntentResult repeated = controller.recognize(request);

        assertThat(first.intentCode()).isEqualTo("ORDER_CANCEL");
        assertThat(first.decision()).isEqualTo(Decision.ASYNC_ACCEPTED);
        assertThat(first.slots()).containsEntry("order_id", "O20260601001");
        assertThat(first.idempotencyKey()).isEqualTo("demo|REQ-P1-HTTP-002|ORDER_CANCEL_COMMAND");
        assertThat(repeated.idempotencyKey()).isEqualTo(first.idempotencyKey());
    }

    @Test
    void rejectsUnknownIntentThroughControllerContract() {
        IntentResult result = controller.recognize(request("REQ-P1-HTTP-004", "给我讲个笑话", null));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.downstreamAction().actionCode()).isEqualTo("NONE");
        assertThat(result.idempotencyKey()).isNull();
    }

    private RecognizeRequest request(String requestId, String text, String traceId) {
        return new RecognizeRequest(
                "demo",
                "app",
                "chat",
                InputType.TEXT,
                text,
                requestId,
                traceId,
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Map.of(),
                List.of()
        );
    }

    private SceneConfig sceneConfig(Envelope envelope) {
        return new SceneConfig(
                envelope.tenantId(),
                "order-scene",
                "v1-p1",
                0.60,
                List.of(
                        new IntentRule("ORDER_QUERY", "CONTAINS", "订单", 0.92, "命中订单查询关键词", Map.of()),
                        new IntentRule("ORDER_CANCEL", "REGEX", "取消订单\\s*([A-Za-z0-9]+)", 0.95, "命中取消订单正则", Map.of("slot_hint", "order_id")),
                        new IntentRule("ORDER_CANCEL", "CONTAINS", "取消订单", 0.93, "命中取消订单关键词但缺少订单号", Map.of())
                ),
                Map.of("ORDER_CANCEL", List.of("order_id")),
                Map.of(
                        "ORDER_QUERY", new DownstreamAction("ORDER_QUERY_SYNC", "NONE", "", false, 0),
                        "ORDER_CANCEL", new DownstreamAction("ORDER_CANCEL_COMMAND", "MQ", "order.command.cancel", true, 3000)
                ),
                new LlmPolicy(false, "spring-ai-alibaba", "qwen-plus", 2000, 0, 0.0, "REJECTED")
        );
    }

    private static final class DisabledLlmClient implements LlmClientPort {
        @Override
        public Optional<RecognitionCandidate> recognize(String text, String tenantId, String sceneId, LlmPolicy policy) {
            return Optional.empty();
        }
    }

    private static final class DisabledModelClient implements ModelClientPort {
        @Override
        public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
            return Optional.empty();
        }
    }

    private static final class NoopMetricsPort implements IntentMetricsPort {
        @Override
        public void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis) {
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(0, 0, 0, 0, 0, 0.0, 0, 0.0, 0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        }
    }
}
