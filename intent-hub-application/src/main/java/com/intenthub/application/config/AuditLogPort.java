package com.intenthub.application.config;

import java.util.Map;
import java.util.List;

public interface AuditLogPort {
    void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail);

    List<AuditLogEntry> list(String tenantId, String sceneId, String targetType, String targetId, int limit);
}
