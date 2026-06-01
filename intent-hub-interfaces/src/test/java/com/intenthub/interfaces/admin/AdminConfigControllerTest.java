package com.intenthub.interfaces.admin;

import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.config.ConfigBundle;
import com.intenthub.application.config.ConfigVersionAppService;
import com.intenthub.application.config.ConfigVersionInfo;
import com.intenthub.application.config.ConfigVersionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConfigControllerTest {
    private AdminConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminConfigController(new ConfigVersionAppService(new InMemoryPort(), new NoopAuditLogPort()));
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
    }

    @Test
    void importsBundleThroughControllerContract() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        ConfigBundle bundle = new ConfigBundle(source, null, null, null, null, null, null);

        ConfigVersionInfo imported = controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-import", "admin", bundle));

        assertThat(imported.version()).isEqualTo("v-import");
        assertThat(imported.status()).isEqualTo("DRAFT");
    }

    private static final class InMemoryPort implements ConfigVersionPort {
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

        private String key(String tenantId, String sceneId, String version) {
            return tenantId + "|" + sceneId + "|" + version;
        }
    }

    private static final class NoopAuditLogPort implements AuditLogPort {
        @Override
        public void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail) {
        }
    }
}
