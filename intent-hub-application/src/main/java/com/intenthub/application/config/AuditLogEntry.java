package com.intenthub.application.config;

import java.time.Instant;
import java.util.Map;

public record AuditLogEntry(
        Long id,
        String tenantId,
        String sceneId,
        String actor,
        String action,
        String targetType,
        String targetId,
        Map<String, String> detail,
        Instant createdAt
) {
}
