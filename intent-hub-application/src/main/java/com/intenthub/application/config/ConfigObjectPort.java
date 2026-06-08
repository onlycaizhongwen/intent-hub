package com.intenthub.application.config;

import java.util.List;
import java.util.Map;

public interface ConfigObjectPort {
    Map<String, Object> upsert(String tenantId, String sceneId, String version, ConfigObjectType type, Map<String, Object> payload);

    List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type);

    boolean delete(String tenantId, String sceneId, String version, ConfigObjectType type, String objectId);
}
