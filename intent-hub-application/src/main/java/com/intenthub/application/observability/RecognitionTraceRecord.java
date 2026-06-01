package com.intenthub.application.observability;

import com.intenthub.domain.recognition.Decision;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RecognitionTraceRecord(
        String traceId,
        String requestId,
        String tenantId,
        String sceneId,
        String inputType,
        Map<String, Object> inputSnapshot,
        String intentCode,
        Decision decision,
        double confidence,
        Map<String, String> slots,
        List<String> recognitionPath,
        String downstreamActionCode,
        String idempotencyKey,
        Instant createdAt
) {
    public RecognitionTraceRecord {
        inputSnapshot = inputSnapshot == null ? Map.of() : Map.copyOf(inputSnapshot);
        slots = slots == null ? Map.of() : Map.copyOf(slots);
        recognitionPath = recognitionPath == null ? List.of() : List.copyOf(recognitionPath);
    }
}
