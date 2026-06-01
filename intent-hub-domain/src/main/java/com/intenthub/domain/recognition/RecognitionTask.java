package com.intenthub.domain.recognition;

import com.intenthub.domain.config.SceneConfig;

import java.util.List;
import java.util.Optional;

public class RecognitionTask {
    private final Envelope envelope;
    private final SceneConfig sceneConfig;
    private final List<String> recognitionPath;

    public RecognitionTask(Envelope envelope, SceneConfig sceneConfig) {
        this.envelope = envelope;
        this.sceneConfig = sceneConfig;
        this.recognitionPath = new java.util.ArrayList<>();
    }

    public Envelope envelope() {
        return envelope;
    }

    public SceneConfig sceneConfig() {
        return sceneConfig;
    }

    public void markPath(String step) {
        recognitionPath.add(step);
    }

    public List<String> recognitionPath() {
        return List.copyOf(recognitionPath);
    }

    public IntentResult toResult(Optional<RecognitionCandidate> candidate, DownstreamAction action, String idempotencyKey) {
        if (candidate.isEmpty()) {
            return new IntentResult(
                    envelope.traceId(),
                    envelope.requestId(),
                    envelope.tenantId(),
                    sceneConfig.sceneId(),
                    "UNKNOWN",
                    Decision.REJECTED,
                    0.0,
                    java.util.Map.of(),
                    recognitionPath(),
                    "未识别到可执行意图",
                    DownstreamAction.none(),
                    null
            );
        }

        RecognitionCandidate selected = candidate.get();
        Decision decision = decide(selected, action);
        String message = switch (decision) {
            case SUCCESS -> "识别成功";
            case ASYNC_ACCEPTED -> "异步动作已接收";
            case CLARIFY -> "需要补充槽位信息";
            default -> "未识别到可执行意图";
        };

        return new IntentResult(
                envelope.traceId(),
                envelope.requestId(),
                envelope.tenantId(),
                sceneConfig.sceneId(),
                selected.intentCode(),
                decision,
                selected.confidence(),
                selected.slots(),
                recognitionPath(),
                message,
                action,
                idempotencyKey
        );
    }

    public Decision decide(RecognitionCandidate candidate, DownstreamAction action) {
        if (candidate.confidence() < sceneConfig.rejectThreshold()) {
            return Decision.REJECTED;
        }
        if (!candidate.slots().keySet().containsAll(sceneConfig.requiredSlotsFor(candidate.intentCode()))) {
            return Decision.CLARIFY;
        }
        if (action.idempotencyRequired()) {
            return Decision.ASYNC_ACCEPTED;
        }
        return Decision.SUCCESS;
    }
}
