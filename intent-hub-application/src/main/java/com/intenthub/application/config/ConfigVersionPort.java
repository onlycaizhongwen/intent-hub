package com.intenthub.application.config;

import java.util.Optional;

public interface ConfigVersionPort {
    ConfigVersionInfo createDraft(String tenantId, String sceneId, String version, String description, String actor);

    Optional<ConfigVersionInfo> find(String tenantId, String sceneId, String version);

    ConfigBundle exportBundle(String tenantId, String sceneId, String version);

    void importBundle(String tenantId, String sceneId, String version, ConfigBundle bundle, String actor);

    void publish(String tenantId, String sceneId, String version, String actor);

    void rollback(String tenantId, String sceneId, String targetVersion, String actor);
}
