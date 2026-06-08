package com.intenthub.domain.config;

import com.intenthub.domain.recognition.RecognitionCandidate;

import java.util.Map;

public record PostRouteRule(
        int priority,
        String routeTarget,
        String intentCode,
        double minConfidence,
        Map<String, String> slotEquals
) {
    public PostRouteRule {
        slotEquals = slotEquals == null ? Map.of() : Map.copyOf(slotEquals);
    }

    public boolean matches(RecognitionCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        if (intentCode != null && !intentCode.isBlank() && !intentCode.equals(candidate.intentCode())) {
            return false;
        }
        if (candidate.confidence() < minConfidence) {
            return false;
        }
        return slotEquals.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(candidate.slots().get(entry.getKey())));
    }
}
