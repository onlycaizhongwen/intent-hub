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
        if (!active(task)) {
            return Optional.empty();
        }
        task.markPath("LlmRecognizePolicy");
        try {
            Optional<RecognitionCandidate> candidate = llmClientPort.recognize(
                    task.envelope().text(),
                    task.sceneConfig().sceneId(),
                    task.sceneConfig().llmPolicy()
            );
            if (candidate.isEmpty()) {
                task.markPath("LLM_FALLBACK:" + task.sceneConfig().llmPolicy().fallbackDecision());
            }
            return candidate;
        } catch (RuntimeException ex) {
            task.markPath("LLM_FALLBACK:" + task.sceneConfig().llmPolicy().fallbackDecision());
            return Optional.empty();
        }
    }

    private boolean active(RecognitionTask task) {
        return task.sceneConfig().llmPolicy().enabled()
                && task.sceneConfig().llmPolicy().dailyBudget() > 0.0
                && task.sceneConfig().llmPolicy().timeoutMs() > 0;
    }
}
