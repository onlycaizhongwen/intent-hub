package com.intenthub.application.config;

import java.time.Instant;
import java.util.Map;

public record ConfigReviewHistoryEntry(
        Long auditId,
        String stage,
        String action,
        String actor,
        String status,
        String reason,
        String snapshotHash,
        String requiredRole,
        String alternativeRole,
        String objectType,
        Instant occurredAt,
        Map<String, String> detail
) {
    public ConfigReviewHistoryEntry {
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }
}
