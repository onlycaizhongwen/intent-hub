package com.intenthub.application;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecognizeAppServiceTest {
    private RecordingTracePort tracePort;
    private RecordingBadCasePort badCasePort;
    private StableIdempotencyPort idempotencyPort;
    private RecognizeAppService service;

    @BeforeEach
    void setUp() {
        tracePort = new RecordingTracePort();
        badCasePort = new RecordingBadCasePort();
        idempotencyPort = new StableIdempotencyPort();
        service = new RecognizeAppService(
                new TestSceneConfigPort(),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient()
        );
    }

    @Test
    void recognizesOrderQueryAsSuccess() {
        IntentResult result = service.recognize(envelope("REQ-P1-001", "查一下订单"));

        assertThat(result.intentCode()).isEqualTo("ORDER_QUERY");
        assertThat(result.decision()).isEqualTo(Decision.SUCCESS);
        assertThat(result.confidence()).isEqualTo(0.92);
        assertThat(result.idempotencyKey()).isNull();
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v1-p1",
                "RuleRecognitionPolicy",
                "POST_ROUTE:ORDER_QUERY_SYNC"
        );
        assertThat(tracePort.results).containsExactly(result);
        assertThat(badCasePort.results).isEmpty();
    }

    @Test
    void acceptsOrderCancelAsyncAndCreatesStableIdempotencyKey() {
        Envelope envelope = envelope("REQ-P1-002", "取消订单 O20260601001");

        IntentResult first = service.recognize(envelope);
        IntentResult repeated = service.recognize(envelope);

        assertThat(first.intentCode()).isEqualTo("ORDER_CANCEL");
        assertThat(first.decision()).isEqualTo(Decision.ASYNC_ACCEPTED);
        assertThat(first.slots()).containsEntry("order_id", "O20260601001");
        assertThat(first.downstreamAction().actionCode()).isEqualTo("ORDER_CANCEL_COMMAND");
        assertThat(first.idempotencyKey()).isNotBlank();
        assertThat(repeated.idempotencyKey()).isEqualTo(first.idempotencyKey());
        assertThat(idempotencyPort.keys).containsExactly(first.idempotencyKey(), repeated.idempotencyKey());
    }

    @Test
    void clarifiesOrderCancelWhenOrderIdIsMissingWithoutIdempotencyKey() {
        IntentResult result = service.recognize(envelope("REQ-P1-003", "帮我取消订单"));

        assertThat(result.intentCode()).isEqualTo("ORDER_CANCEL");
        assertThat(result.decision()).isEqualTo(Decision.CLARIFY);
        assertThat(result.slots()).isEmpty();
        assertThat(result.idempotencyKey()).isNull();
        assertThat(idempotencyPort.keys).isEmpty();
        assertThat(badCasePort.results).isEmpty();
    }

    @Test
    void rejectsUnknownIntentAndRecordsBadCase() {
        IntentResult result = service.recognize(envelope("REQ-P1-004", "给我讲个笑话"));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.confidence()).isZero();
        assertThat(result.downstreamAction().actionCode()).isEqualTo("NONE");
        assertThat(result.idempotencyKey()).isNull();
        assertThat(badCasePort.results).containsExactly(result);
    }

    private Envelope envelope(String requestId, String text) {
        return new Envelope(
                "demo",
                "app",
                "chat",
                InputType.TEXT,
                text,
                requestId,
                "TRACE-" + requestId,
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Map.of(),
                List.of()
        );
    }

    private static final class TestSceneConfigPort implements SceneConfigPort {
        @Override
        public SceneConfig loadPublishedConfig(Envelope envelope) {
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
                    new LlmPolicy(false, "spring-ai-alibaba", "qwen-plus", 2000, 0, "REJECTED")
            );
        }
    }

    private static final class RecordingTracePort implements RecognitionTracePort {
        private final List<IntentResult> results = new ArrayList<>();

        @Override
        public void record(Envelope envelope, IntentResult result) {
            results.add(result);
        }
    }

    private static final class RecordingBadCasePort implements BadCasePort {
        private final List<IntentResult> results = new ArrayList<>();

        @Override
        public void recordIfNeeded(Envelope envelope, IntentResult result) {
            if (result.decision() == Decision.REJECTED || result.confidence() < 0.60) {
                results.add(result);
            }
        }
    }

    private static final class StableIdempotencyPort implements IdempotencyPort {
        private final List<String> keys = new ArrayList<>();

        @Override
        public String reserve(Envelope envelope, DownstreamAction action) {
            String key = digest(envelope.tenantId() + "|" + envelope.requestId() + "|" + action.actionCode());
            keys.add(key);
            return key;
        }

        private String digest(String raw) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is not available", ex);
            }
        }
    }

    private static final class DisabledLlmClient implements LlmClientPort {
        @Override
        public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
            return Optional.empty();
        }
    }
}
