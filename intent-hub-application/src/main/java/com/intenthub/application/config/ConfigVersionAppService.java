package com.intenthub.application.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        return get(tenantId, sceneId, version, null);
    }

    public ConfigVersionInfo get(String tenantId, String sceneId, String version, List<String> roles) {
        ConfigPermission.requireViewer(roles, tenantId, sceneId, "get config version", auditLogPort);
        requireIdentity(tenantId, sceneId, version);
        return configVersionPort.find(tenantId, sceneId, version)
                .map(info -> withCurrentSnapshotHash(info, snapshotHash(tenantId, sceneId, version)))
                .orElseThrow(() -> new NoSuchElementException("config version not found"));
    }

    public ConfigValidationResult validate(String tenantId, String sceneId, String version) {
        return validate(tenantId, sceneId, version, null);
    }

    public ConfigValidationResult validate(String tenantId, String sceneId, String version, List<String> roles) {
        ConfigPermission.requireViewer(roles, tenantId, sceneId, "validate config version", auditLogPort);
        requireIdentity(tenantId, sceneId, version);
        List<String> errors = new ArrayList<>();
        ConfigVersionInfo info = configVersionPort.find(tenantId, sceneId, version).orElse(null);
        if (info == null) {
            errors.add("config version does not exist");
        } else if (!isValidatableStatus(info.status())) {
            errors.add("config version status must be DRAFT, REVIEWING, APPROVED or PUBLISHED");
        } else {
            validateReferences(configVersionPort.exportBundle(tenantId, sceneId, version), errors);
        }
        return errors.isEmpty() ? ConfigValidationResult.ok() : ConfigValidationResult.failed(errors);
    }

    public ConfigDiffResult diff(String tenantId, String sceneId, String fromVersion, String toVersion) {
        return diff(tenantId, sceneId, fromVersion, toVersion, null);
    }

    public ConfigDiffResult diff(String tenantId, String sceneId, String fromVersion, String toVersion, List<String> roles) {
        ConfigPermission.requireViewer(roles, tenantId, sceneId, "diff config version", auditLogPort);
        requireIdentity(tenantId, sceneId, fromVersion);
        requireIdentity(tenantId, sceneId, toVersion);
        ConfigBundle before = configVersionPort.exportBundle(tenantId, sceneId, fromVersion);
        ConfigBundle after = configVersionPort.exportBundle(tenantId, sceneId, toVersion);
        List<ConfigDiffEntry> entries = new ArrayList<>();
        diffObjects("INTENT", before.intents(), after.intents(), entries);
        diffObjects("SLOT", before.slots(), after.slots(), entries);
        diffObjects("SYNONYM", before.synonyms(), after.synonyms(), entries);
        diffObjects("STRATEGY", before.strategies(), after.strategies(), entries);
        diffObjects("ROUTE", before.routes(), after.routes(), entries);
        diffObjects("DOWNSTREAM_ACTION", before.downstreamActions(), after.downstreamActions(), entries);
        entries.sort(Comparator.comparing(ConfigDiffEntry::objectType).thenComparing(ConfigDiffEntry::objectId));
        int added = (int) entries.stream().filter(entry -> "ADDED".equals(entry.changeType())).count();
        int modified = (int) entries.stream().filter(entry -> "MODIFIED".equals(entry.changeType())).count();
        int removed = (int) entries.stream().filter(entry -> "REMOVED".equals(entry.changeType())).count();
        return new ConfigDiffResult(tenantId, sceneId, fromVersion, toVersion, entries, added, modified, removed);
    }

    public ConfigDryRunReport dryRunPublish(String tenantId, String sceneId, String version, String baseVersion) {
        return dryRunPublish(tenantId, sceneId, version, baseVersion, null);
    }

    public ConfigDryRunReport dryRunPublish(String tenantId, String sceneId, String version, String baseVersion, List<String> roles) {
        ConfigPermission.requireViewer(roles, tenantId, sceneId, "dry-run config version", auditLogPort);
        ConfigValidationResult validation = validate(tenantId, sceneId, version);
        ConfigDiffResult diff = null;
        if (baseVersion != null && !baseVersion.isBlank()) {
            diff = diff(tenantId, sceneId, baseVersion, version);
        }
        return new ConfigDryRunReport(
                tenantId,
                sceneId,
                version,
                validation.valid(),
                validation,
                diff,
                gitOpsFiles(tenantId, sceneId, version)
        );
    }

    public ConfigVersionInfo submitReview(String tenantId, String sceneId, String version, String actor) {
        ConfigValidationResult validation = validate(tenantId, sceneId, version);
        if (!validation.valid()) {
            throw new IllegalStateException(String.join("; ", validation.errors()));
        }
        ConfigVersionInfo current = get(tenantId, sceneId, version);
        if (!"DRAFT".equals(current.status())) {
            throw new IllegalStateException("only DRAFT config version can be submitted for review");
        }
        configVersionPort.updateStatus(tenantId, sceneId, version, "REVIEWING", actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_REVIEW_SUBMITTED", "CONFIG_VERSION", version, Map.of(
                "status", "REVIEWING"
        ));
        return get(tenantId, sceneId, version);
    }

    public ConfigVersionInfo approve(String tenantId, String sceneId, String version, String actor) {
        return approve(tenantId, sceneId, version, actor, null);
    }

    public ConfigVersionInfo approve(String tenantId, String sceneId, String version, String actor, List<String> roles) {
        ConfigPermission.requireApprover(roles, tenantId, sceneId, "approve config version", auditLogPort);
        ConfigValidationResult validation = validate(tenantId, sceneId, version);
        if (!validation.valid()) {
            throw new IllegalStateException(String.join("; ", validation.errors()));
        }
        ConfigVersionInfo current = get(tenantId, sceneId, version);
        if (!"REVIEWING".equals(current.status())) {
            throw new IllegalStateException("only REVIEWING config version can be approved");
        }
        String snapshotHash = snapshotHash(tenantId, sceneId, version);
        configVersionPort.updateStatus(tenantId, sceneId, version, "APPROVED", actor);
        configVersionPort.updateApprovedSnapshotHash(tenantId, sceneId, version, snapshotHash, actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_APPROVED", "CONFIG_VERSION", version, Map.of(
                "status", "APPROVED",
                "snapshotHash", snapshotHash
        ));
        return get(tenantId, sceneId, version);
    }

    public ConfigVersionInfo rejectReview(String tenantId, String sceneId, String version, String actor, String reason) {
        return rejectReview(tenantId, sceneId, version, actor, reason, null);
    }

    public ConfigVersionInfo rejectReview(String tenantId, String sceneId, String version, String actor, String reason, List<String> roles) {
        ConfigPermission.requireApprover(roles, tenantId, sceneId, "reject config review", auditLogPort);
        ConfigVersionInfo current = get(tenantId, sceneId, version);
        if (!"REVIEWING".equals(current.status())) {
            throw new IllegalStateException("only REVIEWING config version can be rejected");
        }
        configVersionPort.updateStatus(tenantId, sceneId, version, "DRAFT", actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_REVIEW_REJECTED", "CONFIG_VERSION", version, Map.of(
                "status", "DRAFT",
                "reason", normalizeReason(reason)
        ));
        return get(tenantId, sceneId, version);
    }

    public ConfigVersionInfo cancelReview(String tenantId, String sceneId, String version, String actor, String reason) {
        return cancelReview(tenantId, sceneId, version, actor, reason, null);
    }

    public ConfigVersionInfo cancelReview(String tenantId, String sceneId, String version, String actor, String reason, List<String> roles) {
        ConfigPermission.requireApprover(roles, tenantId, sceneId, "cancel config review", auditLogPort);
        ConfigVersionInfo current = get(tenantId, sceneId, version);
        if (!"REVIEWING".equals(current.status()) && !"APPROVED".equals(current.status())) {
            throw new IllegalStateException("only REVIEWING or APPROVED config version can be returned to draft");
        }
        configVersionPort.updateStatus(tenantId, sceneId, version, "DRAFT", actor);
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_REVIEW_CANCELLED", "CONFIG_VERSION", version, Map.of(
                "status", "DRAFT",
                "previousStatus", current.status(),
                "reason", normalizeReason(reason)
        ));
        return get(tenantId, sceneId, version);
    }

    public ConfigVersionInfo publish(String tenantId, String sceneId, String version, String actor) {
        return publish(tenantId, sceneId, version, actor, null, null);
    }

    public ConfigVersionInfo publish(String tenantId, String sceneId, String version, String actor, String expectedSnapshotHash) {
        return publish(tenantId, sceneId, version, actor, expectedSnapshotHash, null);
    }

    public ConfigVersionInfo publish(String tenantId, String sceneId, String version, String actor, String expectedSnapshotHash, List<String> roles) {
        ConfigPermission.requirePublisher(roles, tenantId, sceneId, "publish config version", auditLogPort);
        ConfigValidationResult validation = validate(tenantId, sceneId, version);
        if (!validation.valid()) {
            throw new IllegalStateException(String.join("; ", validation.errors()));
        }
        ConfigVersionInfo current = get(tenantId, sceneId, version);
        if ("REVIEWING".equals(current.status())) {
            throw new IllegalStateException("REVIEWING config version must be approved before publish");
        }
        if ("APPROVED".equals(current.status())) {
            requireApprovedSnapshotUnchanged(tenantId, sceneId, version);
        }
        requireExpectedSnapshotHashMatches(current, expectedSnapshotHash);
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
        return exportBundle(tenantId, sceneId, version, actor, null);
    }

    public ConfigBundle exportBundle(String tenantId, String sceneId, String version, String actor, List<String> roles) {
        ConfigPermission.requireViewer(roles, tenantId, sceneId, "export config bundle", auditLogPort);
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

    public ConfigGitOpsExport exportGitOps(String tenantId, String sceneId, String version, String baseVersion, String actor) {
        return exportGitOps(tenantId, sceneId, version, baseVersion, actor, null);
    }

    public ConfigGitOpsExport exportGitOps(String tenantId, String sceneId, String version, String baseVersion, String actor, List<String> roles) {
        ConfigPermission.requireViewer(roles, tenantId, sceneId, "export config gitops", auditLogPort);
        requireIdentity(tenantId, sceneId, version);
        ConfigBundle bundle = configVersionPort.exportBundle(tenantId, sceneId, version);
        ConfigValidationResult validation = validate(tenantId, sceneId, version);
        ConfigDiffResult diff = baseVersion == null || baseVersion.isBlank() ? null : diff(tenantId, sceneId, baseVersion, version);
        List<String> files = gitOpsFiles(tenantId, sceneId, version);
        String prefix = "config/" + tenantId + "/" + sceneId + "/" + version + "/";
        Map<String, Object> content = new LinkedHashMap<>();
        content.put(prefix + "version.json", bundle.version());
        content.put(prefix + "intents.json", bundle.intents());
        content.put(prefix + "slots.json", bundle.slots());
        content.put(prefix + "synonyms.json", bundle.synonyms());
        content.put(prefix + "strategies.json", bundle.strategies());
        content.put(prefix + "routes.json", bundle.routes());
        content.put(prefix + "downstream-actions.json", bundle.downstreamActions());
        content.put(prefix + "dry-run.json", new ConfigDryRunReport(
                tenantId,
                sceneId,
                version,
                validation.valid(),
                validation,
                diff,
                files
        ));
        auditLogPort.record(tenantId, sceneId, actor, "CONFIG_GITOPS_EXPORTED", "CONFIG_VERSION", version, Map.of(
                "version", version
        ));
        return new ConfigGitOpsExport(tenantId, sceneId, version, baseVersion, files, content);
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "not provided" : reason;
    }

    private void requireExpectedSnapshotHashMatches(ConfigVersionInfo current, String expectedSnapshotHash) {
        if (expectedSnapshotHash == null || expectedSnapshotHash.isBlank()) {
            return;
        }
        if (!expectedSnapshotHash.equals(current.currentSnapshotHash())) {
            throw new IllegalStateException("expected snapshot hash does not match current config snapshot");
        }
    }

    private void requireApprovedSnapshotUnchanged(String tenantId, String sceneId, String version) {
        String approvedHash = get(tenantId, sceneId, version).approvedSnapshotHash();
        if (approvedHash == null || approvedHash.isBlank()) {
            approvedHash = latestApprovedSnapshotHash(tenantId, sceneId, version);
        }
        if (approvedHash.isBlank()) {
            throw new IllegalStateException("approved snapshot hash is missing");
        }
        String currentHash = snapshotHash(tenantId, sceneId, version);
        if (!approvedHash.equals(currentHash)) {
            throw new IllegalStateException("approved config snapshot has changed");
        }
    }

    private ConfigVersionInfo withCurrentSnapshotHash(ConfigVersionInfo info, String currentSnapshotHash) {
        return new ConfigVersionInfo(
                info.tenantId(),
                info.sceneId(),
                info.version(),
                info.status(),
                info.description(),
                info.createdBy(),
                info.createdAt(),
                info.publishedAt(),
                info.approvedBy(),
                info.approvedAt(),
                info.approvedSnapshotHash(),
                currentSnapshotHash
        );
    }

    private String latestApprovedSnapshotHash(String tenantId, String sceneId, String version) {
        return auditLogPort.list(tenantId, sceneId, "CONFIG_VERSION", version, 100).stream()
                .filter(entry -> "CONFIG_APPROVED".equals(entry.action()))
                .map(entry -> entry.detail().getOrDefault("snapshotHash", ""))
                .filter(hash -> !hash.isBlank())
                .findFirst()
                .orElse("");
    }

    private String snapshotHash(String tenantId, String sceneId, String version) {
        ConfigBundle bundle = configVersionPort.exportBundle(tenantId, sceneId, version);
        String canonical = canonical(bundle.intents())
                + canonical(bundle.slots())
                + canonical(bundle.synonyms())
                + canonical(bundle.strategies())
                + canonical(bundle.routes())
                + canonical(bundle.downstreamActions());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .forEach(entry -> result.append(entry.getKey()).append(":").append(canonical(entry.getValue())).append(";"));
            return result.append("}").toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder result = new StringBuilder("[");
            for (Object item : iterable) {
                result.append(canonical(item)).append(",");
            }
            return result.append("]").toString();
        }
        return value.toString();
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

    private boolean isValidatableStatus(String status) {
        return "DRAFT".equals(status)
                || "REVIEWING".equals(status)
                || "APPROVED".equals(status)
                || "PUBLISHED".equals(status);
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

        for (Map<String, Object> action : bundle.downstreamActions()) {
            String actionCode = string(action, "actionCode", "action_code", "downstreamActionId", "downstream_action_id");
            String intentCode = explicitActionIntentCode(action);
            if (intentCode.isBlank()) {
                intentCode = inferIntentCode(actionCode);
            }
            if (!intentCode.isBlank() && !intentCodes.contains(intentCode)) {
                errors.add("downstream action " + actionCode + " references missing intent " + intentCode);
            }
        }
    }

    private void diffObjects(String objectType, List<Map<String, Object>> before, List<Map<String, Object>> after, List<ConfigDiffEntry> entries) {
        Map<String, Map<String, Object>> beforeById = indexByObjectId(objectType, before);
        Map<String, Map<String, Object>> afterById = indexByObjectId(objectType, after);
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(beforeById.keySet());
        ids.addAll(afterById.keySet());
        for (String id : ids) {
            Map<String, Object> left = beforeById.get(id);
            Map<String, Object> right = afterById.get(id);
            if (left == null) {
                entries.add(new ConfigDiffEntry(objectType, id, "ADDED", null, right));
            } else if (right == null) {
                entries.add(new ConfigDiffEntry(objectType, id, "REMOVED", left, null));
            } else if (!Objects.equals(left, right)) {
                entries.add(new ConfigDiffEntry(objectType, id, "MODIFIED", left, right));
            }
        }
    }

    private Map<String, Map<String, Object>> indexByObjectId(String objectType, List<Map<String, Object>> values) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> value = values.get(index);
            result.put(objectId(objectType, value, index), value);
        }
        return result;
    }

    private String objectId(String objectType, Map<String, Object> value, int index) {
        String candidate = switch (objectType) {
            case "INTENT" -> string(value, "intentCode", "intent_code");
            case "SLOT" -> compositeId(index,
                    string(value, "intentCode", "intent_code"),
                    string(value, "slotCode", "slot_code"));
            case "SYNONYM" -> string(value, "term");
            case "STRATEGY" -> string(value, "strategyCode", "strategy_code");
            case "ROUTE" -> compositeId(index,
                    string(value, "routeStage", "route_stage"),
                    string(value, "routeTarget", "route_target", "downstreamActionId", "downstream_action_id"));
            case "DOWNSTREAM_ACTION" -> string(value, "actionCode", "action_code", "downstreamActionId", "downstream_action_id");
            default -> "";
        };
        return candidate.isBlank() ? indexedId(index) : candidate;
    }

    private String compositeId(int index, String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return indexedId(index);
        }
        return left + "." + right;
    }

    private String indexedId(int index) {
        return "__index_" + index;
    }

    private List<String> gitOpsFiles(String tenantId, String sceneId, String version) {
        String prefix = "config/" + tenantId + "/" + sceneId + "/" + version + "/";
        return List.of(
                prefix + "version.json",
                prefix + "intents.json",
                prefix + "slots.json",
                prefix + "synonyms.json",
                prefix + "strategies.json",
                prefix + "routes.json",
                prefix + "downstream-actions.json"
        );
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

    @SuppressWarnings("unchecked")
    private String explicitActionIntentCode(Map<String, Object> action) {
        String intentCode = string(action, "intentCode", "intent_code");
        if (!intentCode.isBlank()) {
            return intentCode;
        }
        Object schema = firstPresent(action, "actionSchema", "action_schema");
        if (schema instanceof Map<?, ?> map) {
            return string((Map<String, Object>) map, "intentCode", "intent_code");
        }
        return "";
    }

    private Object firstPresent(Map<String, Object> value, String... keys) {
        for (String key : keys) {
            Object candidate = value.get(key);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
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
