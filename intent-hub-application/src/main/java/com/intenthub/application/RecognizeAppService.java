package com.intenthub.application;

import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentRecognizer;
import com.intenthub.domain.recognition.IntentResult;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.RecognitionTask;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import com.intenthub.domain.recognition.policy.LlmRecognizePolicy;
import com.intenthub.domain.recognition.policy.RuleRecognitionPolicy;
import com.intenthub.application.metrics.IntentMetricsPort;

import java.util.List;
import java.util.Optional;

public class RecognizeAppService {
    private final SceneConfigPort sceneConfigPort;
    private final RecognitionTracePort recognitionTracePort;
    private final BadCasePort badCasePort;
    private final IdempotencyPort idempotencyPort;
    private final LlmClientPort llmClientPort;
    private final IntentMetricsPort metricsPort;

    public RecognizeAppService(
            SceneConfigPort sceneConfigPort,
            RecognitionTracePort recognitionTracePort,
            BadCasePort badCasePort,
            IdempotencyPort idempotencyPort,
            LlmClientPort llmClientPort,
            IntentMetricsPort metricsPort
    ) {
        this.sceneConfigPort = sceneConfigPort;
        this.recognitionTracePort = recognitionTracePort;
        this.badCasePort = badCasePort;
        this.idempotencyPort = idempotencyPort;
        this.llmClientPort = llmClientPort;
        this.metricsPort = metricsPort;
    }

    public IntentResult recognize(Envelope envelope) {
        long startedAt = System.nanoTime();
        SceneConfig sceneConfig = sceneConfigPort.loadPublishedConfig(envelope);
        RecognitionTask task = new RecognitionTask(envelope, sceneConfig);

        task.markPath("PRE_ROUTE:" + sceneConfig.sceneId() + ":" + sceneConfig.version());
        IntentRecognizer recognizer = new IntentRecognizer(List.of(
                new RuleRecognitionPolicy(),
                new LlmRecognizePolicy(llmClientPort)
        ));

        Optional<RecognitionCandidate> candidate = recognizer.recognize(task);
        DownstreamAction action = candidate
                .map(RecognitionCandidate::intentCode)
                .map(sceneConfig::actionFor)
                .orElse(DownstreamAction.none());

        task.markPath("POST_ROUTE:" + action.actionCode());
        String idempotencyKey = candidate
                .filter(selected -> task.decide(selected, action) == Decision.ASYNC_ACCEPTED)
                .map(selected -> idempotencyPort.reserve(envelope, action))
                .orElse(null);
        IntentResult result = task.toResult(candidate, action, idempotencyKey);

        recognitionTracePort.record(envelope, result);
        badCasePort.recordIfNeeded(envelope, result);
        metricsPort.recordRecognition(envelope, result, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return result;
    }
}
