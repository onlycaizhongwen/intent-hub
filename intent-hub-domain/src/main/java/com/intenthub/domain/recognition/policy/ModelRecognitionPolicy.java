package com.intenthub.domain.recognition.policy;

import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.RecognitionTask;

import java.util.Optional;

public class ModelRecognitionPolicy implements RecognitionPolicy {
    private final ModelClientPort modelClientPort;

    public ModelRecognitionPolicy(ModelClientPort modelClientPort) {
        this.modelClientPort = modelClientPort;
    }

    @Override
    public Optional<RecognitionCandidate> recognize(RecognitionTask task) {
        if (!task.sceneConfig().modelPolicy().enabled()) {
            task.markPath("MODEL_POLICY:DISABLED");
            return Optional.empty();
        }
        try {
            Optional<RecognitionCandidate> candidate = modelClientPort.recognize(task.envelope().text(), task.sceneConfig().sceneId());
            if (candidate.isPresent() && candidate.get().confidence() < task.sceneConfig().modelPolicy().minConfidence()) {
                task.markPath("MODEL_POLICY:LOW_CONFIDENCE");
                return Optional.empty();
            }
            candidate.ifPresent(ignored -> task.markPath("ModelRecognitionPolicy"));
            return candidate;
        } catch (RuntimeException ex) {
            task.markPath("MODEL_FALLBACK:CLOSED");
            return Optional.empty();
        }
    }
}
