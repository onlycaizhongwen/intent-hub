package com.intenthub.application.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigVersionAppServiceTest {
    private InMemoryPort port;
    private RecordingAuditLogPort auditLogPort;
    private ConfigVersionAppService service;

    @BeforeEach
    void setUp() {
        port = new InMemoryPort();
        auditLogPort = new RecordingAuditLogPort();
        service = new ConfigVersionAppService(port, auditLogPort);
    }

    @Test
    void managesDraftValidationPublishRollbackAndExport() {
        ConfigVersionInfo v1 = service.createDraft("demo", "order-scene", "v1", "base", "admin");
        ConfigVersionInfo v2 = service.createDraft("demo", "order-scene", "v2", "next", "admin");

        assertThat(v1.status()).isEqualTo("DRAFT");
        assertThat(service.validate("demo", "order-scene", "v1").valid()).isTrue();

        ConfigVersionInfo publishedV1 = service.publish("demo", "order-scene", "v1", "admin");
        assertThat(publishedV1.status()).isEqualTo("PUBLISHED");
        assertThat(publishedV1.publishedAt()).isNotNull();

        ConfigVersionInfo publishedV2 = service.publish("demo", "order-scene", "v2", "admin");
        assertThat(publishedV2.status()).isEqualTo("PUBLISHED");
        assertThat(service.get("demo", "order-scene", "v1").status()).isEqualTo("ARCHIVED");

        ConfigVersionInfo rolledBack = service.rollback("demo", "order-scene", "v1", "admin");
        assertThat(rolledBack.status()).isEqualTo("PUBLISHED");
        assertThat(service.get("demo", "order-scene", "v2").status()).isEqualTo("ARCHIVED");

        ConfigBundle exported = service.exportBundle("demo", "order-scene", "v1", "admin");
        assertThat(exported.version().version()).isEqualTo("v1");
        assertThat(auditLogPort.actions).contains(
                "CONFIG_DRAFT_CREATED",
                "CONFIG_PUBLISHED",
                "CONFIG_ROLLED_BACK",
                "CONFIG_EXPORTED"
        );
    }

    @Test
    void importsBundleAsDraftAndRecordsAudit() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "external bundle", "ops", Instant.now(), Instant.now());
        ConfigBundle bundle = new ConfigBundle(source, List.of(Map.of("intent_code", "ORDER_QUERY")), null, null, null, null, null);

        ConfigVersionInfo imported = service.importBundle("demo", "order-scene", "v-imported", bundle, "admin");

        assertThat(imported.version()).isEqualTo("v-imported");
        assertThat(imported.status()).isEqualTo("DRAFT");
        assertThat(imported.description()).isEqualTo("external bundle");
        assertThat(service.exportBundle("demo", "order-scene", "v-imported", "admin").intents()).hasSize(1);
        assertThat(auditLogPort.actions).contains("CONFIG_IMPORTED");
    }

    @Test
    void upsertsConfigObjectsOnlyForDraftVersions() {
        service.createDraft("demo", "order-scene", "v1", "base", "admin");
        ConfigObjectAppService objectService = new ConfigObjectAppService(port, port, auditLogPort);

        Map<String, Object> saved = objectService.upsert("demo", "order-scene", "v1", ConfigObjectType.INTENT, Map.of(
                "intentCode", "ORDER_QUERY",
                "intentName", "订单查询"
        ), "admin");

        assertThat(saved).containsEntry("intentCode", "ORDER_QUERY");
        assertThat(objectService.list("demo", "order-scene", "v1", ConfigObjectType.INTENT)).hasSize(1);
        assertThat(service.exportBundle("demo", "order-scene", "v1", "admin").intents()).hasSize(1);
        assertThat(auditLogPort.actions).contains("CONFIG_OBJECT_UPSERTED");

        service.publish("demo", "order-scene", "v1", "admin");
        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v1", ConfigObjectType.INTENT, Map.of(
                "intentCode", "ORDER_CANCEL",
                "intentName", "订单取消"
        ), "admin")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only DRAFT");
    }

    @Test
    void preservesModelPolicyWhenUpsertingStrategy() {
        service.createDraft("demo", "order-scene", "v-model-policy", "model policy", "admin");
        ConfigObjectAppService objectService = new ConfigObjectAppService(port, port, auditLogPort);

        Map<String, Object> saved = objectService.upsert("demo", "order-scene", "v-model-policy", ConfigObjectType.STRATEGY, Map.of(
                "strategyCode", "default",
                "confidenceThreshold", 0.60,
                "modelPolicy", Map.of(
                        "enabled", false,
                        "endpoint", "https://model.example.test",
                        "timeoutMs", 1800,
                        "minConfidence", 0.72
                )
        ), "admin");

        assertThat(saved).containsKey("modelPolicy");
        assertThat(saved.get("modelPolicy")).isEqualTo(Map.of(
                "enabled", false,
                "endpoint", "https://model.example.test",
                "timeoutMs", 1800,
                "minConfidence", 0.72
        ));
    }

    @Test
    void listsVersionAuditsByTargetVersion() {
        service.createDraft("demo", "order-scene", "v-audit", "audit", "admin");
        service.publish("demo", "order-scene", "v-audit", "admin");
        service.createDraft("demo", "order-scene", "v-other", "other", "admin");

        ConfigAuditAppService auditService = new ConfigAuditAppService(auditLogPort);

        assertThat(auditService.listVersionAudits("demo", "order-scene", "v-audit", 1))
                .extracting(AuditLogEntry::action)
                .containsExactly("CONFIG_PUBLISHED");
    }

    private static final class InMemoryPort implements ConfigVersionPort, ConfigObjectPort {
        private final Map<String, ConfigBundle> bundles = new LinkedHashMap<>();

        @Override
        public ConfigVersionInfo createDraft(String tenantId, String sceneId, String version, String description, String actor) {
            ConfigVersionInfo info = new ConfigVersionInfo(tenantId, sceneId, version, "DRAFT", description, actor, Instant.now(), null);
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(info, null, null, null, null, null, null));
            return info;
        }

        @Override
        public Optional<ConfigVersionInfo> find(String tenantId, String sceneId, String version) {
            return Optional.ofNullable(bundles.get(key(tenantId, sceneId, version))).map(ConfigBundle::version);
        }

        @Override
        public ConfigBundle exportBundle(String tenantId, String sceneId, String version) {
            return bundles.get(key(tenantId, sceneId, version));
        }

        @Override
        public void importBundle(String tenantId, String sceneId, String version, ConfigBundle bundle, String actor) {
            ConfigVersionInfo source = bundle.version();
            ConfigVersionInfo info = new ConfigVersionInfo(tenantId, sceneId, version, "DRAFT", source.description(), actor, Instant.now(), null);
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(info, bundle.intents(), bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions()));
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
                return withInfo(bundle, new ConfigVersionInfo(current.tenantId(), current.sceneId(), current.version(), status, current.description(), current.createdBy(), current.createdAt(), version.equals(current.version()) ? Instant.now() : current.publishedAt()));
            });
        }

        @Override
        public void rollback(String tenantId, String sceneId, String targetVersion, String actor) {
            publish(tenantId, sceneId, targetVersion, actor);
        }

        @Override
        public Map<String, Object> upsert(String tenantId, String sceneId, String version, ConfigObjectType type, Map<String, Object> payload) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            List<Map<String, Object>> intents = type == ConfigObjectType.INTENT ? List.of(payload) : bundle.intents();
            List<Map<String, Object>> strategies = type == ConfigObjectType.STRATEGY ? List.of(payload) : bundle.strategies();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(bundle.version(), intents, bundle.slots(), bundle.synonyms(), strategies, bundle.routes(), bundle.downstreamActions()));
            return payload;
        }

        @Override
        public List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            if (type == ConfigObjectType.INTENT) {
                return bundle.intents();
            }
            if (type == ConfigObjectType.STRATEGY) {
                return bundle.strategies();
            }
            return List.of();
        }

        private ConfigBundle withInfo(ConfigBundle bundle, ConfigVersionInfo info) {
            return new ConfigBundle(info, bundle.intents(), bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions());
        }

        private String key(String tenantId, String sceneId, String version) {
            return tenantId + "|" + sceneId + "|" + version;
        }
    }

    private static final class RecordingAuditLogPort implements AuditLogPort {
        private final List<String> actions = new ArrayList<>();
        private final List<AuditLogEntry> entries = new ArrayList<>();

        @Override
        public void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail) {
            actions.add(action);
            entries.add(new AuditLogEntry(
                    (long) entries.size() + 1,
                    tenantId,
                    sceneId,
                    actor,
                    action,
                    targetType,
                    targetId,
                    detail,
                    Instant.now()
            ));
        }

        @Override
        public List<AuditLogEntry> list(String tenantId, String sceneId, String targetType, String targetId, int limit) {
            return entries.stream()
                    .filter(entry -> tenantId.equals(entry.tenantId()))
                    .filter(entry -> sceneId.equals(entry.sceneId()))
                    .filter(entry -> targetType.equals(entry.targetType()))
                    .filter(entry -> targetId.equals(entry.targetId()))
                    .sorted((left, right) -> Long.compare(right.id(), left.id()))
                    .limit(limit)
                    .toList();
        }
    }
}
