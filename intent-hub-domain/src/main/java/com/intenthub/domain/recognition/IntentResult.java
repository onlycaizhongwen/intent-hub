package com.intenthub.domain.recognition;

import java.util.List;
import java.util.Map;

public record IntentResult(
        String traceId,
        String requestId,
        String tenantId,
        String sceneId,
        String intentCode,
        Decision decision,
        double confidence,
        Map<String, String> slots,
        List<String> recognitionPath,
        String message,
        DownstreamAction downstreamAction,
        String idempotencyKey
) {
    public IntentResult {
        slots = slots == null ? Map.of() : Map.copyOf(slots);
        recognitionPath = recognitionPath == null ? List.of() : List.copyOf(recognitionPath);
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0.0, 1.0]");
        }
    }
}
