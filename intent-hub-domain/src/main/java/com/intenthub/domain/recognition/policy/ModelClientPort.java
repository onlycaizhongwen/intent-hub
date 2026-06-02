package com.intenthub.domain.recognition.policy;

import com.intenthub.domain.recognition.RecognitionCandidate;

import java.util.Optional;

public interface ModelClientPort {
    Optional<RecognitionCandidate> recognize(String text, String sceneId);

    default boolean healthy() {
        return false;
    }
}
