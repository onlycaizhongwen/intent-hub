package com.intenthub.application.observability;

import com.intenthub.domain.recognition.Decision;

import java.time.Instant;
import java.util.Map;

public record BadCaseRecord(
        String traceId,
        String requestId,
        String tenantId,
        String sceneId,
        String intentCode,
        Decision decision,
        double confidence,
        String reason,
        Map<String, Object> inputSnapshot,
        String status,
        Instant createdAt
) {
    public BadCaseRecord {
        inputSnapshot = inputSnapshot == null ? Map.of() : Map.copyOf(inputSnapshot);
    }
}
