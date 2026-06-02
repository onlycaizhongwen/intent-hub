package com.intenthub.domain.recognition.policy;

import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.recognition.RecognitionCandidate;

import java.util.Optional;

public interface LlmClientPort {
    Optional<RecognitionCandidate> recognize(String text, String sceneId, LlmPolicy policy);
}
