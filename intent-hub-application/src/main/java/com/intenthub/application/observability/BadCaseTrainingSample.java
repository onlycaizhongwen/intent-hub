package com.intenthub.application.observability;

import java.time.Instant;
import java.util.Map;

public record BadCaseTrainingSample(
        String traceId,
        String requestId,
        String tenantId,
        String sceneId,
        String text,
        String intentCode,
        String decision,
        double confidence,
        String reason,
        String status,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public BadCaseTrainingSample {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
