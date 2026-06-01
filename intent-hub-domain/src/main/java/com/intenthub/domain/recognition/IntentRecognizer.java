package com.intenthub.domain.recognition;

import com.intenthub.domain.recognition.policy.RecognitionPolicy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class IntentRecognizer {
    private final List<RecognitionPolicy> policies;

    public IntentRecognizer(List<RecognitionPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    public Optional<RecognitionCandidate> recognize(RecognitionTask task) {
        return policies.stream()
                .flatMap(policy -> policy.recognize(task).stream())
                .max(Comparator.comparingDouble(RecognitionCandidate::confidence));
    }
}
