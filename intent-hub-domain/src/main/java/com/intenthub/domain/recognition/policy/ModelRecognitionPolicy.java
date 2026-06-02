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
        Optional<RecognitionCandidate> candidate = modelClientPort.recognize(task.envelope().text(), task.sceneConfig().sceneId());
        candidate.ifPresent(ignored -> task.markPath("ModelRecognitionPolicy"));
        return candidate;
    }
}
