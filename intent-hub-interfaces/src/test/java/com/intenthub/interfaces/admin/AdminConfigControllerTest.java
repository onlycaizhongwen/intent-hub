package com.intenthub.interfaces.admin;

import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.config.AuditLogEntry;
import com.intenthub.application.config.ConfigBundle;
import com.intenthub.application.config.ConfigAuditAppService;
import com.intenthub.application.config.ConfigObjectAppService;
import com.intenthub.application.config.ConfigObjectPort;
import com.intenthub.application.config.ConfigObjectType;
import com.intenthub.application.config.ConfigVersionAppService;
import com.intenthub.application.config.ConfigVersionInfo;
import com.intenthub.application.config.ConfigVersionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConfigControllerTest {
    private AdminConfigController controller;

    @BeforeEach
    void setUp() {
        InMemoryPort port = new InMemoryPort();
        NoopAuditLogPort auditLogPort = new NoopAuditLogPort();
        controller = new AdminConfigController(
                new ConfigVersionAppService(port, auditLogPort),
                new ConfigObjectAppService(port, port, auditLogPort),
                new ConfigAuditAppService(auditLogPort)
        );
    }

    @Test
    void managesConfigVersionLifecycleThroughControllerContract() {
        ConfigVersionInfo draft = controller.createDraft(new ConfigDraftRequest("demo", "order-scene", "v1", "base", "admin"));

        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(controller.validate("demo", "order-scene", "v1").valid()).isTrue();

        ConfigVersionInfo published = controller.publish("demo", "order-scene", "v1", new ConfigVersionActionRequest("admin"));
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isNotNull();

        ConfigBundle exported = controller.exportBundle("demo", "order-scene", "v1", "admin");
        assertThat(exported.version().version()).isEqualTo("v1");

        List<AuditLogEntry> audits = controller.listVersionAudits("demo", "order-scene", "v1", 10);
        assertThat(audits).extracting(AuditLogEntry::action)
                .containsExactly("CONFIG_EXPORTED", "CONFIG_PUBLISHED", "CONFIG_DRAFT_CREATED");
    }

    @Test
    void importsBundleThroughControllerContract() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        ConfigBundle bundle = new ConfigBundle(source, null, null, null, null, null, null);

        ConfigVersionInfo imported = controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-import", "admin", bundle));

        assertThat(imported.version()).isEqualTo("v-import");
        assertThat(imported.status()).isEqualTo("DRAFT");
    }

    @Test
    void managesConfigObjectsThroughControllerContract() {
        controller.createDraft(new ConfigDraftRequest("demo", "order-scene", "v1", "base", "admin"));

        Map<String, Object> saved = controller.upsertConfigObject("demo", "order-scene", "v1", "intents", new ConfigObjectRequest("admin", Map.of(
                "intentCode", "ORDER_QUERY",
                "intentName", "订单查询"
        )));

        assertThat(saved).containsEntry("intentCode", "ORDER_QUERY");
        assertThat(controller.listConfigObjects("demo", "order-scene", "v1", "intents")).hasSize(1);
        assertThat(controller.exportBundle("demo", "order-scene", "v1", "admin").intents()).hasSize(1);
    }

    @Test
    void bulkUpsertsAndDeletesConfigObjectsThroughControllerContract() {
        controller.createDraft(new ConfigDraftRequest("demo", "order-scene", "v-bulk", "base", "admin"));

        List<Map<String, Object>> saved = controller.bulkUpsertConfigObjects("demo", "order-scene", "v-bulk", "intents", new ConfigObjectBulkRequest("admin", List.of(
                Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询"),
                Map.of("intentCode", "ORDER_CANCEL", "intentName", "订单取消")
        )));

        assertThat(saved).hasSize(2);
        assertThat(controller.listConfigObjects("demo", "order-scene", "v-bulk", "intents")).hasSize(2);
        assertThat(controller.deleteConfigObject("demo", "order-scene", "v-bulk", "intents", "ORDER_QUERY", "admin"))
                .containsEntry("deleted", true);
        assertThat(controller.listConfigObjects("demo", "order-scene", "v-bulk", "intents")).extracting(item -> item.get("intentCode"))
                .containsExactly("ORDER_CANCEL");
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
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            ConfigVersionInfo current = bundle.version();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(
                    new ConfigVersionInfo(current.tenantId(), current.sceneId(), current.version(), "PUBLISHED", current.description(), current.createdBy(), current.createdAt(), Instant.now()),
                    bundle.intents(), bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions()
            ));
        }

        @Override
        public void rollback(String tenantId, String sceneId, String targetVersion, String actor) {
            publish(tenantId, sceneId, targetVersion, actor);
        }

        @Override
        public Map<String, Object> upsert(String tenantId, String sceneId, String version, ConfigObjectType type, Map<String, Object> payload) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            List<Map<String, Object>> intents = new java.util.ArrayList<>(bundle.intents() == null ? List.of() : bundle.intents());
            intents.removeIf(item -> payload.get("intentCode").equals(item.get("intentCode")));
            intents.add(payload);
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(bundle.version(), intents, bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions()));
            return payload;
        }

        @Override
        public java.util.List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type) {
            return bundles.get(key(tenantId, sceneId, version)).intents();
        }

        @Override
        public boolean delete(String tenantId, String sceneId, String version, ConfigObjectType type, String objectId) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            List<Map<String, Object>> current = bundle.intents() == null ? List.of() : bundle.intents();
            List<Map<String, Object>> remaining = current.stream()
                    .filter(item -> !objectId.equals(item.get("intentCode")))
                    .toList();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(bundle.version(), remaining, bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions()));
            return remaining.size() != current.size();
        }

        private String key(String tenantId, String sceneId, String version) {
            return tenantId + "|" + sceneId + "|" + version;
        }
    }

    private static final class NoopAuditLogPort implements AuditLogPort {
        private final List<AuditLogEntry> entries = new java.util.ArrayList<>();

        @Override
        public void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail) {
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
