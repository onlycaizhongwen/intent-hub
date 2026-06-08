package com.intenthub.application.config;

import java.util.List;

public class ConfigAuditAppService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final AuditLogPort auditLogPort;

    public ConfigAuditAppService(AuditLogPort auditLogPort) {
        this.auditLogPort = auditLogPort;
    }

    public List<AuditLogEntry> listVersionAudits(String tenantId, String sceneId, String version, Integer limit) {
        requireIdentity(tenantId, sceneId, version);
        int normalizedLimit = normalizeLimit(limit);
        return auditLogPort.list(tenantId, sceneId, "CONFIG_VERSION", version, normalizedLimit);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void requireIdentity(String tenantId, String sceneId, String version) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (sceneId == null || sceneId.isBlank()) {
            throw new IllegalArgumentException("sceneId is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
    }
}
