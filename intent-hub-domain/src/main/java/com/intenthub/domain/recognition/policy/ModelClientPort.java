package com.intenthub.domain.recognition.policy;

import com.intenthub.domain.config.ModelPolicy;
import com.intenthub.domain.recognition.RecognitionCandidate;

import java.util.Optional;

public interface ModelClientPort {
    Optional<RecognitionCandidate> recognize(String text, String sceneId);

    default Optional<RecognitionCandidate> recognize(String text, String sceneId, ModelPolicy policy) {
        return recognize(text, sceneId);
    }

    default boolean healthy() {
        return false;
    }

    default ModelServiceHealth healthDetails() {
        return new ModelServiceHealth(healthy(), null, null);
    }
}
