package com.intenthub.application.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class ConfigObjectAppService {
    private static final int MIN_TIMEOUT_MS = 1;
    private static final int MAX_TIMEOUT_MS = 60_000;
    private static final int MAX_LLM_RETRIES = 5;
    private static final Set<String> ROUTE_STAGES = new LinkedHashSet<>(List.of("PRE", "POST"));
    private static final Set<String> ACTION_TYPES = new LinkedHashSet<>(List.of("API", "MQ", "WEBHOOK", "MQTT"));

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

    public List<Map<String, Object>> bulkUpsert(String tenantId, String sceneId, String version, ConfigObjectType type, List<Map<String, Object>> payloads, String actor) {
        requireDraftVersion(tenantId, sceneId, version);
        if (payloads == null || payloads.isEmpty()) {
            throw new IllegalArgumentException("payloads is required");
        }
        List<Map<String, Object>> saved = payloads.stream()
                .map(payload -> configObjectPort.upsert(tenantId, sceneId, version, type, normalize(type, payload)))
                .toList();
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_OBJECT_BULK_UPSERTED", type.name(), type.name(), Map.of(
                "version", version,
                "count", Integer.toString(saved.size())
        ));
        return saved;
    }

    public List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type) {
        requireVersion(tenantId, sceneId, version);
        return configObjectPort.list(tenantId, sceneId, version, type);
    }

    public boolean delete(String tenantId, String sceneId, String version, ConfigObjectType type, String objectId, String actor) {
        requireDraftVersion(tenantId, sceneId, version);
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId is required");
        }
        boolean deleted = configObjectPort.delete(tenantId, sceneId, version, type, objectId);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_OBJECT_DELETED", type.name(), objectId, Map.of(
                "version", version,
                "deleted", Boolean.toString(deleted)
        ));
        return deleted;
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
                    "confidenceThreshold", decimalRange(payload, "confidenceThreshold", "0.600", "0.0", "1.0"),
                    "llmPolicy", policyMap(payload, "llmPolicy"),
                    "modelPolicy", policyMap(payload, "modelPolicy")
            );
            case ROUTE -> mapOf(
                    "routeStage", enumValue(payload, "routeStage", ROUTE_STAGES),
                    "priority", integerRange(payload, "priority", 0, 0, Integer.MAX_VALUE),
                    "matchCondition", objectMap(payload, "matchCondition"),
                    "routeTarget", required(payload, "routeTarget")
            );
            case DOWNSTREAM_ACTION -> mapOf(
                    "actionCode", required(payload, "actionCode"),
                    "actionType", enumValue(payload, "actionType", ACTION_TYPES),
                    "target", required(payload, "target"),
                    "idempotencyRequired", bool(payload, "idempotencyRequired", false),
                    "timeoutMs", integerRange(payload, "timeoutMs", 3000, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
                    "actionSchema", downstreamActionSchema(payload)
            );
        };
    }

    private Map<String, Object> downstreamActionSchema(Map<String, Object> payload) {
        Map<String, Object> schema = objectMap(payload, "actionSchema");
        Object intentCode = payload.get("intentCode");
        if (intentCode == null || intentCode.toString().isBlank()) {
            return schema;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(schema);
        Object schemaIntentCode = normalized.get("intentCode");
        if (schemaIntentCode != null && !schemaIntentCode.toString().isBlank() && !schemaIntentCode.toString().equals(intentCode.toString())) {
            throw new IllegalArgumentException("intentCode must match actionSchema.intentCode");
        }
        normalized.put("intentCode", intentCode.toString());
        return normalized;
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

    private int integerRange(Map<String, Object> payload, String key, int defaultValue, int min, int max) {
        int value = integer(payload, key, defaultValue);
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private BigDecimal decimal(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return new BigDecimal(defaultValue);
        }
        return new BigDecimal(value.toString());
    }

    private BigDecimal decimalRange(Map<String, Object> payload, String key, String defaultValue, String min, String max) {
        BigDecimal value = decimal(payload, key, defaultValue);
        BigDecimal lower = new BigDecimal(min);
        BigDecimal upper = new BigDecimal(max);
        if (value.compareTo(lower) < 0 || value.compareTo(upper) > 0) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private String enumValue(Map<String, Object> payload, String key, Set<String> allowedValues) {
        String value = required(payload, key).toString().toUpperCase();
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(key + " must be one of " + allowedValues);
        }
        return value;
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

    private Map<String, Object> policyMap(Map<String, Object> payload, String key) {
        Map<String, Object> policy = objectMap(payload, key);
        if (policy.isEmpty()) {
            return policy;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(policy);
        validatePolicyFields(normalized, key);
        return normalized;
    }

    private void validatePolicyFields(Map<String, Object> policy, String key) {
        if (policy.containsKey("enabled")) {
            policy.put("enabled", bool(policy, "enabled", false));
        }
        if (policy.containsKey("timeoutMs")) {
            policy.put("timeoutMs", policyIntegerRange(policy, key, "timeoutMs", MIN_TIMEOUT_MS, MAX_TIMEOUT_MS));
        }
        if (policy.containsKey("minConfidence")) {
            policyDecimalRange(policy, key, "minConfidence", "0.0", "1.0");
        }
        if (policy.containsKey("dailyBudget")) {
            policyDecimalRange(policy, key, "dailyBudget", "0.0", "999999999.0");
        }
        if (policy.containsKey("maxRetries")) {
            policy.put("maxRetries", policyIntegerRange(policy, key, "maxRetries", 0, MAX_LLM_RETRIES));
        }
    }

    private int policyIntegerRange(Map<String, Object> policy, String policyKey, String field, int min, int max) {
        try {
            return integerRange(policy, field, 0, min, max);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(policyKey + "." + ex.getMessage());
        }
    }

    private BigDecimal policyDecimalRange(Map<String, Object> policy, String policyKey, String field, String min, String max) {
        try {
            return decimalRange(policy, field, min, min, max);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(policyKey + "." + ex.getMessage());
        }
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
