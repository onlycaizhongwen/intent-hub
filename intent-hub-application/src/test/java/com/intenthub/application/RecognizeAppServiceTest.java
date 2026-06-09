package com.intenthub.application;

import com.intenthub.domain.config.IntentRule;
import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.config.ModelPolicy;
import com.intenthub.domain.config.PostRouteRule;
import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.InputType;
import com.intenthub.domain.recognition.IntentResult;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import com.intenthub.domain.recognition.policy.ModelClientPort;
import com.intenthub.domain.recognition.policy.ModelServiceAuthenticationException;
import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
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
    private RecordingMetricsPort metricsPort;
    private RecognizeAppService service;

    @BeforeEach
    void setUp() {
        tracePort = new RecordingTracePort();
        badCasePort = new RecordingBadCasePort();
        idempotencyPort = new StableIdempotencyPort();
        metricsPort = new RecordingMetricsPort();
        service = new RecognizeAppService(
                new TestSceneConfigPort(),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient(),
                metricsPort,
                new DisabledModelClient()
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
        assertThat(metricsPort.results).containsExactly(result);
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
    void selectsPostRouteActionBySlotConditionBeforeIntentFallback() {
        IntentResult result = service.recognize(envelope("REQ-P2-POST-ROUTE-001", "取消订单 VIP100"));

        assertThat(result.intentCode()).isEqualTo("ORDER_CANCEL");
        assertThat(result.downstreamAction().actionCode()).isEqualTo("VIP_CANCEL_COMMAND");
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v1-p1",
                "RuleRecognitionPolicy",
                "POST_ROUTE:VIP_CANCEL_COMMAND"
        );
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

    @Test
    void usesModelCandidateWhenRulesDoNotMatchAndKeepsLlmAsLaterFallback() {
        service = new RecognizeAppService(
                new TestSceneConfigPort(),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient(),
                metricsPort,
                (text, sceneId) -> Optional.of(new RecognitionCandidate("ORDER_QUERY", 0.80, Map.of(), "model hit"))
        );

        IntentResult result = service.recognize(envelope("REQ-P2-MODEL-001", "模型判断这是查单"));

        assertThat(result.intentCode()).isEqualTo("ORDER_QUERY");
        assertThat(result.decision()).isEqualTo(Decision.SUCCESS);
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v1-p1",
                "RuleRecognitionPolicy",
                "ModelRecognitionPolicy",
                "POST_ROUTE:ORDER_QUERY_SYNC"
        );
    }

    @Test
    void failsClosedWhenModelServiceThrowsAndContinuesToReject() {
        service = new RecognizeAppService(
                new TestSceneConfigPort(),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient(),
                metricsPort,
                (text, sceneId) -> {
                    throw new IllegalStateException("model unavailable");
                }
        );

        IntentResult result = service.recognize(envelope("REQ-P2-MODEL-002", "模型服务异常场景"));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v1-p1",
                "RuleRecognitionPolicy",
                "MODEL_FALLBACK:CLOSED",
                "POST_ROUTE:NONE"
        );
        assertThat(tracePort.results).containsExactly(result);
        assertThat(badCasePort.results).containsExactly(result);
    }

    @Test
    void failsClosedWithAuthMarkerWhenModelServiceTokenReferenceIsMissing() {
        service = new RecognizeAppService(
                new TestSceneConfigPort(),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient(),
                metricsPort,
                new ModelClientPort() {
                    @Override
                    public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<RecognitionCandidate> recognize(String text, String sceneId, ModelPolicy policy) {
                        throw new ModelServiceAuthenticationException("MODEL_FALLBACK:AUTH_MISSING_TOKEN");
                    }
                }
        );

        IntentResult result = service.recognize(envelope("REQ-P2-MODEL-AUTH-001", "模型服务缺少鉴权 token"));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v1-p1",
                "RuleRecognitionPolicy",
                "MODEL_FALLBACK:AUTH_MISSING_TOKEN",
                "POST_ROUTE:NONE"
        );
        assertThat(tracePort.results).containsExactly(result);
        assertThat(badCasePort.results).containsExactly(result);
    }

    @Test
    void skipsModelWhenSceneModelPolicyIsDisabled() {
        service = new RecognizeAppService(
                envelope -> sceneWithModelPolicy(envelope, ModelPolicy.disabled()),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient(),
                metricsPort,
                (text, sceneId) -> {
                    throw new AssertionError("model service should be skipped by scene policy");
                }
        );

        IntentResult result = service.recognize(envelope("REQ-P2-MODEL-POLICY-001", "model only text"));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v-model-policy",
                "RuleRecognitionPolicy",
                "MODEL_POLICY:DISABLED",
                "POST_ROUTE:NONE"
        );
    }

    @Test
    void rejectsLowConfidenceModelCandidateBeforePostRoute() {
        service = new RecognizeAppService(
                envelope -> sceneWithModelPolicy(envelope, new ModelPolicy(true, "", 0, 0.85)),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient(),
                metricsPort,
                (text, sceneId) -> Optional.of(new RecognitionCandidate("ORDER_QUERY", 0.80, Map.of(), "model hit"))
        );

        IntentResult result = service.recognize(envelope("REQ-P2-MODEL-POLICY-002", "model low confidence"));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v-model-policy",
                "RuleRecognitionPolicy",
                "MODEL_POLICY:LOW_CONFIDENCE",
                "POST_ROUTE:NONE"
        );
    }

    @Test
    void passesSceneModelPolicyToModelClient() {
        ModelPolicy policy = new ModelPolicy(true, "http://scene-model.example.test", 1234, 0.70);
        RecordingModelClient modelClient = new RecordingModelClient(new RecognitionCandidate("ORDER_QUERY", 0.80, Map.of(), "scene model hit"));
        service = new RecognizeAppService(
                envelope -> sceneWithModelPolicy(envelope, policy),
                tracePort,
                badCasePort,
                idempotencyPort,
                new DisabledLlmClient(),
                metricsPort,
                modelClient
        );

        IntentResult result = service.recognize(envelope("REQ-P2-MODEL-POLICY-003", "model only text"));

        assertThat(result.intentCode()).isEqualTo("ORDER_QUERY");
        assertThat(modelClient.lastPolicy).isEqualTo(policy);
        assertThat(modelClient.lastSceneId).isEqualTo("order-scene");
    }

    @Test
    void failsClosedWhenLlmFallbackThrows() {
        service = new RecognizeAppService(
                envelope -> llmEnabledScene(envelope, 10.0),
                tracePort,
                badCasePort,
                idempotencyPort,
                (text, tenantId, sceneId, policy) -> {
                    throw new IllegalStateException("llm unavailable");
                },
                metricsPort,
                new DisabledModelClient()
        );

        IntentResult result = service.recognize(envelope("REQ-P2-LLM-001", "长尾复杂表达"));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v-llm",
                "RuleRecognitionPolicy",
                "LlmRecognizePolicy",
                "LLM_FALLBACK:REJECTED",
                "POST_ROUTE:NONE"
        );
        assertThat(badCasePort.results).containsExactly(result);
    }

    @Test
    void doesNotEnterLlmWhenPolicyBudgetIsZero() {
        service = new RecognizeAppService(
                envelope -> llmEnabledScene(envelope, 0.0),
                tracePort,
                badCasePort,
                idempotencyPort,
                (text, tenantId, sceneId, policy) -> {
                    throw new AssertionError("LLM should be blocked by zero policy budget");
                },
                metricsPort,
                new DisabledModelClient()
        );

        IntentResult result = service.recognize(envelope("REQ-P2-LLM-002", "长尾复杂表达"));

        assertThat(result.intentCode()).isEqualTo("UNKNOWN");
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.recognitionPath()).containsExactly(
                "PRE_ROUTE:order-scene:v-llm",
                "RuleRecognitionPolicy",
                "POST_ROUTE:NONE"
        );
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

    private SceneConfig llmEnabledScene(Envelope envelope, double dailyBudget) {
        return new SceneConfig(
                envelope.tenantId(),
                "order-scene",
                "v-llm",
                0.60,
                List.of(),
                Map.of(),
                Map.of("UNKNOWN", DownstreamAction.none()),
                List.of(),
                ModelPolicy.enabledByDefault(),
                new LlmPolicy(true, "spring-ai-alibaba", "qwen-plus", 2000, 0, dailyBudget, "REJECTED")
        );
    }

    private SceneConfig sceneWithModelPolicy(Envelope envelope, ModelPolicy modelPolicy) {
        return new SceneConfig(
                envelope.tenantId(),
                "order-scene",
                "v-model-policy",
                0.60,
                List.of(),
                Map.of(),
                Map.of("ORDER_QUERY", new DownstreamAction("ORDER_QUERY_SYNC", "NONE", "", false, 0)),
                List.of(),
                modelPolicy,
                LlmPolicy.disabled()
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
                            "ORDER_CANCEL", new DownstreamAction("ORDER_CANCEL_COMMAND", "MQ", "order.command.cancel", true, 3000),
                            "VIP_CANCEL", new DownstreamAction("VIP_CANCEL_COMMAND", "MQ", "order.command.cancel.vip", true, 3000)
                    ),
                    List.of(
                            new PostRouteRule(0, "VIP_CANCEL", "ORDER_CANCEL", 0.90, Map.of("order_id", "VIP100"))
                    ),
                    ModelPolicy.enabledByDefault(),
                    new LlmPolicy(false, "spring-ai-alibaba", "qwen-plus", 2000, 0, 0.0, "REJECTED")
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

    private static final class RecordingMetricsPort implements IntentMetricsPort {
        private final List<IntentResult> results = new ArrayList<>();

        @Override
        public void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis) {
            results.add(result);
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(0, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        }
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

    private static final class RecordingModelClient implements ModelClientPort {
        private final RecognitionCandidate candidate;
        private String lastSceneId;
        private ModelPolicy lastPolicy;

        private RecordingModelClient(RecognitionCandidate candidate) {
            this.candidate = candidate;
        }

        @Override
        public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
            return Optional.of(candidate);
        }

        @Override
        public Optional<RecognitionCandidate> recognize(String text, String sceneId, ModelPolicy policy) {
            lastSceneId = sceneId;
            lastPolicy = policy;
            return Optional.of(candidate);
        }
    }
}
