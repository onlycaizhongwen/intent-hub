package com.intenthub.infrastructure.config;

import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.config.AuditLogEntry;
import com.intenthub.application.config.ConfigBundle;
import com.intenthub.application.config.ConfigObjectPort;
import com.intenthub.application.config.ConfigObjectType;
import com.intenthub.application.config.ConfigVersionInfo;
import com.intenthub.application.config.ConfigVersionPort;
import com.intenthub.application.metrics.IntentMetricsPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryConfigGovernanceRepository implements ConfigVersionPort, ConfigObjectPort, AuditLogPort {
    private static final String PERMISSION_DENIED_ACTION = "CONFIG_PERMISSION_DENIED";

    private final Map<String, ConfigBundle> bundles = new LinkedHashMap<>();
    private final List<AuditLogEntry> auditLogs = new ArrayList<>();
    private final IntentMetricsPort metricsPort;
    private long auditSequence = 0L;

    public InMemoryConfigGovernanceRepository(IntentMetricsPort metricsPort) {
        this.metricsPort = metricsPort;
    }

    @Override
    public ConfigVersionInfo createDraft(String tenantId, String sceneId, String version, String description, String actor) {
        ConfigVersionInfo info = new ConfigVersionInfo(tenantId, sceneId, version, "DRAFT", description, actor, Instant.now(), null);
        bundles.put(key(tenantId, sceneId, version), emptyBundle(info));
        return info;
    }

    @Override
    public Optional<ConfigVersionInfo> find(String tenantId, String sceneId, String version) {
        return Optional.ofNullable(bundles.get(key(tenantId, sceneId, version))).map(ConfigBundle::version);
    }

    @Override
    public ConfigBundle exportBundle(String tenantId, String sceneId, String version) {
        ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
        if (bundle == null) {
            throw new IllegalArgumentException("config version not found");
        }
        return bundle;
    }

    @Override
    public void importBundle(String tenantId, String sceneId, String version, ConfigBundle bundle, String actor) {
        ConfigVersionInfo source = bundle == null ? null : bundle.version();
        ConfigVersionInfo info = new ConfigVersionInfo(
                tenantId,
                sceneId,
                version,
                "DRAFT",
                source == null ? "Imported config bundle" : source.description(),
                actor,
                Instant.now(),
                null
        );
        bundles.put(key(tenantId, sceneId, version), new ConfigBundle(
                info,
                bundle == null ? null : bundle.intents(),
                bundle == null ? null : bundle.slots(),
                bundle == null ? null : bundle.synonyms(),
                bundle == null ? null : bundle.strategies(),
                bundle == null ? null : bundle.routes(),
                bundle == null ? null : bundle.downstreamActions()
        ));
    }

    @Override
    public void updateStatus(String tenantId, String sceneId, String version, String status, String actor) {
        String key = key(tenantId, sceneId, version);
        ConfigBundle bundle = bundles.get(key);
        if (bundle == null) {
            throw new IllegalArgumentException("config version not found");
        }
        ConfigVersionInfo current = bundle.version();
        bundles.put(key, withVersion(bundle, new ConfigVersionInfo(
                current.tenantId(),
                current.sceneId(),
                current.version(),
                status,
                current.description(),
                current.createdBy(),
                current.createdAt(),
                current.publishedAt(),
                current.approvedBy(),
                current.approvedAt(),
                current.approvedSnapshotHash(),
                current.currentSnapshotHash()
        )));
    }

    @Override
    public void updateApprovedSnapshotHash(String tenantId, String sceneId, String version, String snapshotHash, String actor) {
        String key = key(tenantId, sceneId, version);
        ConfigBundle bundle = bundles.get(key);
        if (bundle == null) {
            throw new IllegalArgumentException("config version not found");
        }
        ConfigVersionInfo current = bundle.version();
        bundles.put(key, withVersion(bundle, new ConfigVersionInfo(
                current.tenantId(),
                current.sceneId(),
                current.version(),
                current.status(),
                current.description(),
                current.createdBy(),
                current.createdAt(),
                current.publishedAt(),
                actor,
                Instant.now(),
                snapshotHash,
                current.currentSnapshotHash()
        )));
    }

    @Override
    public void publish(String tenantId, String sceneId, String version, String actor) {
        bundles.replaceAll((candidateKey, bundle) -> {
            ConfigVersionInfo current = bundle.version();
            if (!tenantId.equals(current.tenantId()) || !sceneId.equals(current.sceneId())) {
                return bundle;
            }
            String status = current.status();
            if (version.equals(current.version())) {
                status = "PUBLISHED";
            } else if ("PUBLISHED".equals(current.status())) {
                status = "ARCHIVED";
            }
            return withVersion(bundle, new ConfigVersionInfo(
                    current.tenantId(),
                    current.sceneId(),
                    current.version(),
                    status,
                    current.description(),
                    current.createdBy(),
                    current.createdAt(),
                    version.equals(current.version()) ? Instant.now() : current.publishedAt(),
                    current.approvedBy(),
                    current.approvedAt(),
                    current.approvedSnapshotHash(),
                    current.currentSnapshotHash()
            ));
        });
    }

    @Override
    public void rollback(String tenantId, String sceneId, String targetVersion, String actor) {
        publish(tenantId, sceneId, targetVersion, actor);
    }

    @Override
    public void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail) {
        if (PERMISSION_DENIED_ACTION.equals(action)) {
            metricsPort.recordPermissionDenied(tenantId, sceneId, detail == null ? null : detail.get("action"));
        }
        auditLogs.add(new AuditLogEntry(
                ++auditSequence,
                tenantId,
                sceneId,
                actor,
                action,
                targetType,
                targetId,
                detail == null ? Map.of() : new LinkedHashMap<>(detail),
                Instant.now()
        ));
    }

    @Override
    public List<AuditLogEntry> list(String tenantId, String sceneId, String targetType, String targetId, int limit) {
        return auditLogs.stream()
                .filter(entry -> tenantId.equals(entry.tenantId()))
                .filter(entry -> sceneId.equals(entry.sceneId()))
                .filter(entry -> targetType.equals(entry.targetType()))
                .filter(entry -> targetId.equals(entry.targetId()))
                .sorted((left, right) -> Long.compare(right.id(), left.id()))
                .limit(limit)
                .toList();
    }

    @Override
    public Map<String, Object> upsert(String tenantId, String sceneId, String version, ConfigObjectType type, Map<String, Object> payload) {
        String key = key(tenantId, sceneId, version);
        ConfigBundle bundle = bundles.get(key);
        if (bundle == null) {
            throw new IllegalArgumentException("config version not found");
        }
        List<Map<String, Object>> target = new ArrayList<>(listFrom(bundle, type));
        String objectKey = objectKey(type, payload);
        target.removeIf(candidate -> objectKey.equals(objectKey(type, candidate)));
        target.add(new LinkedHashMap<>(payload));
        bundles.put(key, replaceList(bundle, type, target));
        return payload;
    }

    @Override
    public List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type) {
        ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
        if (bundle == null) {
            throw new IllegalArgumentException("config version not found");
        }
        return listFrom(bundle, type);
    }

    @Override
    public boolean delete(String tenantId, String sceneId, String version, ConfigObjectType type, String objectId) {
        String key = key(tenantId, sceneId, version);
        ConfigBundle bundle = bundles.get(key);
        if (bundle == null) {
            throw new IllegalArgumentException("config version not found");
        }
        List<Map<String, Object>> current = listFrom(bundle, type);
        List<Map<String, Object>> remaining = current.stream()
                .filter(candidate -> !objectId.equals(objectKey(type, candidate)))
                .toList();
        if (remaining.size() == current.size()) {
            return false;
        }
        bundles.put(key, replaceList(bundle, type, remaining));
        return true;
    }

    private ConfigBundle emptyBundle(ConfigVersionInfo info) {
        return new ConfigBundle(info, null, null, null, null, null, null);
    }

    private ConfigBundle withVersion(ConfigBundle bundle, ConfigVersionInfo info) {
        return new ConfigBundle(info, bundle.intents(), bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions());
    }

    private List<Map<String, Object>> listFrom(ConfigBundle bundle, ConfigObjectType type) {
        return switch (type) {
            case INTENT -> bundle.intents();
            case SLOT -> bundle.slots();
            case SYNONYM -> bundle.synonyms();
            case STRATEGY -> bundle.strategies();
            case ROUTE -> bundle.routes();
            case DOWNSTREAM_ACTION -> bundle.downstreamActions();
        };
    }

    private ConfigBundle replaceList(ConfigBundle bundle, ConfigObjectType type, List<Map<String, Object>> values) {
        return new ConfigBundle(
                bundle.version(),
                type == ConfigObjectType.INTENT ? values : bundle.intents(),
                type == ConfigObjectType.SLOT ? values : bundle.slots(),
                type == ConfigObjectType.SYNONYM ? values : bundle.synonyms(),
                type == ConfigObjectType.STRATEGY ? values : bundle.strategies(),
                type == ConfigObjectType.ROUTE ? values : bundle.routes(),
                type == ConfigObjectType.DOWNSTREAM_ACTION ? values : bundle.downstreamActions()
        );
    }

    private String objectKey(ConfigObjectType type, Map<String, Object> payload) {
        return switch (type) {
            case INTENT -> payload.get("intentCode").toString();
            case SLOT -> payload.get("intentCode") + "|" + payload.get("slotCode");
            case SYNONYM -> payload.get("term").toString();
            case STRATEGY -> payload.get("strategyCode").toString();
            case ROUTE -> payload.get("routeStage") + "|" + payload.get("routeTarget");
            case DOWNSTREAM_ACTION -> payload.get("actionCode").toString();
        };
    }

    private String key(String tenantId, String sceneId, String version) {
        return tenantId + "|" + sceneId + "|" + version;
    }
}
