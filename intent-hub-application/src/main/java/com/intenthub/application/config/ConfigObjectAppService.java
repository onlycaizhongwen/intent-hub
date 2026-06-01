package com.intenthub.application.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class ConfigObjectAppService {
    private final ConfigVersionPort configVersionPort;
    private final ConfigObjectPort configObjectPort;
    private final AuditLogPort auditLogPort;

    public ConfigObjectAppService(ConfigVersionPort configVersionPort, ConfigObjectPort configObjectPort, AuditLogPort auditLogPort) {
        this.configVersionPort = configVersionPort;
        this.configObjectPort = configObjectPort;
        this.auditLogPort = auditLogPort;
    }

    public Map<String, Object> upsert(String tenantId, String sceneId, String version, ConfigObjectType type, Map<String, Object> payload, String actor) {
        requireDraftVersion(tenantId, sceneId, version);
        Map<String, Object> normalized = normalize(type, payload);
        Map<String, Object> saved = configObjectPort.upsert(tenantId, sceneId, version, type, normalized);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_OBJECT_UPSERTED", type.name(), objectId(type, saved), Map.of(
                "version", version
        ));
        return saved;
    }

    public List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type) {
        requireVersion(tenantId, sceneId, version);
        return configObjectPort.list(tenantId, sceneId, version, type);
    }

    private void requireDraftVersion(String tenantId, String sceneId, String version) {
        ConfigVersionInfo info = requireVersion(tenantId, sceneId, version);
        if (!"DRAFT".equals(info.status())) {
            throw new IllegalStateException("only DRAFT config version can be edited");
        }
    }

    private ConfigVersionInfo requireVersion(String tenantId, String sceneId, String version) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (sceneId == null || sceneId.isBlank()) {
            throw new IllegalArgumentException("sceneId is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        return configVersionPort.find(tenantId, sceneId, version)
                .orElseThrow(() -> new NoSuchElementException("config version not found"));
    }

    private Map<String, Object> normalize(ConfigObjectType type, Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        return switch (type) {
            case INTENT -> mapOf(
                    "intentCode", required(payload, "intentCode"),
                    "intentName", required(payload, "intentName"),
                    "enabled", bool(payload, "enabled", true),
                    "definition", objectMap(payload, "definition")
            );
            case SLOT -> mapOf(
                    "intentCode", required(payload, "intentCode"),
                    "slotCode", required(payload, "slotCode"),
                    "required", bool(payload, "required", false),
                    "definition", objectMap(payload, "definition")
            );
            case SYNONYM -> mapOf(
                    "term", required(payload, "term"),
                    "normalizedTerm", required(payload, "normalizedTerm")
            );
            case STRATEGY -> mapOf(
                    "strategyCode", required(payload, "strategyCode"),
                    "confidenceThreshold", decimal(payload, "confidenceThreshold", "0.600"),
                    "llmPolicy", objectMap(payload, "llmPolicy")
            );
            case ROUTE -> mapOf(
                    "routeStage", required(payload, "routeStage"),
                    "priority", integer(payload, "priority", 0),
                    "matchCondition", objectMap(payload, "matchCondition"),
                    "routeTarget", required(payload, "routeTarget")
            );
            case DOWNSTREAM_ACTION -> mapOf(
                    "actionCode", required(payload, "actionCode"),
                    "actionType", required(payload, "actionType"),
                    "target", required(payload, "target"),
                    "idempotencyRequired", bool(payload, "idempotencyRequired", false),
                    "timeoutMs", integer(payload, "timeoutMs", 3000),
                    "actionSchema", objectMap(payload, "actionSchema")
            );
        };
    }

    private String objectId(ConfigObjectType type, Map<String, Object> payload) {
        return switch (type) {
            case INTENT -> string(payload.get("intentCode"));
            case SLOT -> string(payload.get("intentCode")) + "." + string(payload.get("slotCode"));
            case SYNONYM -> string(payload.get("term"));
            case STRATEGY -> string(payload.get("strategyCode"));
            case ROUTE -> string(payload.get("routeStage")) + "." + string(payload.get("routeTarget"));
            case DOWNSTREAM_ACTION -> string(payload.get("actionCode"));
        };
    }

    private Object required(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.toString();
    }

    private boolean bool(Map<String, Object> payload, String key, boolean defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private int integer(Map<String, Object> payload, String key, int defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private BigDecimal decimal(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return new BigDecimal(defaultValue);
        }
        return new BigDecimal(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        throw new IllegalArgumentException(key + " must be an object");
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index].toString(), values[index + 1]);
        }
        return map;
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
