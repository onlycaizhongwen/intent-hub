package com.intenthub.application.config;

import java.time.Instant;

public record ConfigVersionInfo(
        String tenantId,
        String sceneId,
        String version,
        String status,
        String description,
        String createdBy,
        Instant createdAt,
        Instant publishedAt
) {
}
