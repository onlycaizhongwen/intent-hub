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
        Instant publishedAt,
        String approvedBy,
        Instant approvedAt,
        String approvedSnapshotHash,
        String currentSnapshotHash
) {
    public ConfigVersionInfo(
            String tenantId,
            String sceneId,
            String version,
            String status,
            String description,
            String createdBy,
            Instant createdAt,
            Instant publishedAt
    ) {
        this(tenantId, sceneId, version, status, description, createdBy, createdAt, publishedAt, null, null, null, null);
    }

    public ConfigVersionInfo(
            String tenantId,
            String sceneId,
            String version,
            String status,
            String description,
            String createdBy,
            Instant createdAt,
            Instant publishedAt,
            String approvedSnapshotHash,
            String currentSnapshotHash
    ) {
        this(tenantId, sceneId, version, status, description, createdBy, createdAt, publishedAt, null, null, approvedSnapshotHash, currentSnapshotHash);
    }
}
