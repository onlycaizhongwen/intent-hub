package com.intenthub.application.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class ConfigVersionAppService {
    private final ConfigVersionPort configVersionPort;
    private final AuditLogPort auditLogPort;

    public ConfigVersionAppService(ConfigVersionPort configVersionPort, AuditLogPort auditLogPort) {
        this.configVersionPort = configVersionPort;
        this.auditLogPort = auditLogPort;
    }

    public ConfigVersionInfo createDraft(String tenantId, String sceneId, String version, String description, String actor) {
        requireIdentity(tenantId, sceneId, version);
        ConfigVersionInfo draft = configVersionPort.createDraft(tenantId, sceneId, version, description, actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_DRAFT_CREATED", "CONFIG_VERSION", version, Map.of(
                "status", draft.status()
        ));
        return draft;
    }

    public ConfigVersionInfo get(String tenantId, String sceneId, String version) {
        requireIdentity(tenantId, sceneId, version);
        return configVersionPort.find(tenantId, sceneId, version)
                .orElseThrow(() -> new NoSuchElementException("config version not found"));
    }

    public ConfigValidationResult validate(String tenantId, String sceneId, String version) {
        requireIdentity(tenantId, sceneId, version);
        List<String> errors = new ArrayList<>();
        ConfigVersionInfo info = configVersionPort.find(tenantId, sceneId, version).orElse(null);
        if (info == null) {
            errors.add("config version does not exist");
        } else if (!"DRAFT".equals(info.status()) && !"PUBLISHED".equals(info.status())) {
            errors.add("config version status must be DRAFT or PUBLISHED");
        } else {
            validateReferences(configVersionPort.exportBundle(tenantId, sceneId, version), errors);
        }
        return errors.isEmpty() ? ConfigValidationResult.ok() : ConfigValidationResult.failed(errors);
    }

    public ConfigVersionInfo publish(String tenantId, String sceneId, String version, String actor) {
        ConfigValidationResult validation = validate(tenantId, sceneId, version);
        if (!validation.valid()) {
            throw new IllegalStateException(String.join("; ", validation.errors()));
        }
        configVersionPort.publish(tenantId, sceneId, version, actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_PUBLISHED", "CONFIG_VERSION", version, Map.of(
                "publishedVersion", version
        ));
        return get(tenantId, sceneId, version);
    }

    public ConfigVersionInfo rollback(String tenantId, String sceneId, String targetVersion, String actor) {
        requireIdentity(tenantId, sceneId, targetVersion);
        if (configVersionPort.find(tenantId, sceneId, targetVersion).isEmpty()) {
            throw new NoSuchElementException("target config version not found");
        }
        configVersionPort.rollback(tenantId, sceneId, targetVersion, actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_ROLLED_BACK", "CONFIG_VERSION", targetVersion, Map.of(
                "targetVersion", targetVersion
        ));
        return get(tenantId, sceneId, targetVersion);
    }

    public ConfigBundle exportBundle(String tenantId, String sceneId, String version, String actor) {
        requireIdentity(tenantId, sceneId, version);
        ConfigBundle bundle = configVersionPort.exportBundle(tenantId, sceneId, version);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_EXPORTED", "CONFIG_VERSION", version, Map.of(
                "version", version
        ));
        return bundle;
    }

    public ConfigVersionInfo importBundle(String tenantId, String sceneId, String version, ConfigBundle bundle, String actor) {
        requireIdentity(tenantId, sceneId, version);
        configVersionPort.importBundle(tenantId, sceneId, version, bundle, actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_IMPORTED", "CONFIG_VERSION", version, Map.of(
                "version", version
        ));
        return get(tenantId, sceneId, version);
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

    private void validateReferences(ConfigBundle bundle, List<String> errors) {
        Set<String> intentCodes = collectValues(bundle.intents(), "intentCode", "intent_code");
        Set<String> actionCodes = collectValues(bundle.downstreamActions(), "actionCode", "action_code", "downstreamActionId", "downstream_action_id");

        for (Map<String, Object> slot : bundle.slots()) {
            String intentCode = string(slot, "intentCode", "intent_code");
            String slotCode = string(slot, "slotCode", "slot_code");
            if (!intentCode.isBlank() && !intentCodes.contains(intentCode)) {
                errors.add("slot " + intentCode + "." + slotCode + " references missing intent " + intentCode);
            }
        }

        for (Map<String, Object> route : bundle.routes()) {
            String routeStage = string(route, "routeStage", "route_stage");
            String routeTarget = string(route, "routeTarget", "route_target", "downstreamActionId", "downstream_action_id");
            if ("POST".equalsIgnoreCase(routeStage) && !routeTarget.isBlank() && !actionCodes.contains(routeTarget)) {
                errors.add("POST route " + routeTarget + " references missing downstream action");
            }
        }

        for (String actionCode : actionCodes) {
            String intentCode = inferIntentCode(actionCode);
            if (!intentCode.isBlank() && !intentCodes.contains(intentCode)) {
                errors.add("downstream action " + actionCode + " references missing intent " + intentCode);
            }
        }
    }

    private Set<String> collectValues(List<Map<String, Object>> values, String... keys) {
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> value : values) {
            String item = string(value, keys);
            if (!item.isBlank()) {
                result.add(item);
            }
        }
        return result;
    }

    private String string(Map<String, Object> value, String... keys) {
        for (String key : keys) {
            Object candidate = value.get(key);
            if (candidate != null && !candidate.toString().isBlank()) {
                return candidate.toString();
            }
        }
        return "";
    }

    private String inferIntentCode(String actionCode) {
        if (actionCode.endsWith("_API")) {
            return actionCode.substring(0, actionCode.length() - 4);
        }
        if (actionCode.endsWith("_SYNC")) {
            return actionCode.substring(0, actionCode.length() - 5);
        }
        if (actionCode.endsWith("_COMMAND")) {
            return actionCode.substring(0, actionCode.length() - 8);
        }
        if (actionCode.endsWith("_WEBHOOK")) {
            return actionCode.substring(0, actionCode.length() - 8);
        }
        return actionCode;
    }
}
