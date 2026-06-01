package com.intenthub.domain.recognition.policy;

import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.RecognitionTask;

import java.util.Optional;

public interface RecognitionPolicy {
    Optional<RecognitionCandidate> recognize(RecognitionTask task);
}
