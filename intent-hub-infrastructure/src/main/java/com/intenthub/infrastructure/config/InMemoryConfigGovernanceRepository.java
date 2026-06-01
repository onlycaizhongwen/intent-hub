package com.intenthub.infrastructure.config;

import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.config.ConfigBundle;
import com.intenthub.application.config.ConfigVersionInfo;
import com.intenthub.application.config.ConfigVersionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryConfigGovernanceRepository implements ConfigVersionPort, AuditLogPort {
    private final Map<String, ConfigBundle> bundles = new LinkedHashMap<>();

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
                    version.equals(current.version()) ? Instant.now() : current.publishedAt()
            ));
        });
    }

    @Override
    public void rollback(String tenantId, String sceneId, String targetVersion, String actor) {
        publish(tenantId, sceneId, targetVersion, actor);
    }

    @Override
    public void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail) {
        // Memory mode keeps audit side effects in-process only; P1 JDBC mode persists audit_log.
    }

    private ConfigBundle emptyBundle(ConfigVersionInfo info) {
        return new ConfigBundle(info, null, null, null, null, null, null);
    }

    private ConfigBundle withVersion(ConfigBundle bundle, ConfigVersionInfo info) {
        return new ConfigBundle(info, bundle.intents(), bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions());
    }

    private String key(String tenantId, String sceneId, String version) {
        return tenantId + "|" + sceneId + "|" + version;
    }
}
