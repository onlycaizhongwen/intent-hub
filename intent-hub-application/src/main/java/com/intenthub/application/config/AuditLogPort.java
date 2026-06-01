package com.intenthub.application.config;

import java.util.Map;

public interface AuditLogPort {
    void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail);
}
