package com.intenthub.infrastructure.model;

import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.ModelClientPort;

import java.util.Optional;

public class NoopModelClientAdapter implements ModelClientPort {
    @Override
    public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
        return Optional.empty();
    }
}
