package com.intenthub.domain.recognition;

import java.util.Map;

public record RecognitionCandidate(
        String intentCode,
        double confidence,
        Map<String, String> slots,
        String explanation
) {
    public RecognitionCandidate {
        slots = slots == null ? Map.of() : Map.copyOf(slots);
    }
}
