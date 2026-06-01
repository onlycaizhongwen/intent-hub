package com.intenthub.domain.recognition.policy;

import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.RecognitionTask;

import java.util.Optional;

public class LlmRecognizePolicy implements RecognitionPolicy {
    private final LlmClientPort llmClientPort;

    public LlmRecognizePolicy(LlmClientPort llmClientPort) {
        this.llmClientPort = llmClientPort;
    }

    @Override
    public Optional<RecognitionCandidate> recognize(RecognitionTask task) {
        if (!task.sceneConfig().llmPolicy().enabled()) {
            return Optional.empty();
        }
        task.markPath("LlmRecognizePolicy");
        return llmClientPort.recognize(task.envelope().text(), task.sceneConfig().sceneId());
    }
}
