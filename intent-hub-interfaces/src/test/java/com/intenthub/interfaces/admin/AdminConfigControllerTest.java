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
import com.intenthub.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminConfigControllerTest {
    private AdminConfigController controller;

    @BeforeEach
    void setUp() {
        InMemoryPort port = new InMemoryPort();
        NoopAuditLogPort auditLogPort = new NoopAuditLogPort();
        controller = new AdminConfigController(
                new ConfigVersionAppService(port, auditLogPort),
                new ConfigObjectAppService(port, port, auditLogPort),
                new ConfigAuditAppService(auditLogPort),
                new com.intenthub.application.config.ConfigReviewWorkspaceAppService(
                        new ConfigVersionAppService(port, auditLogPort),
                        new ConfigAuditAppService(auditLogPort)
                )
        );
    }

    @Test
    void managesConfigVersionLifecycleThroughControllerContract() {
        ConfigVersionInfo draft = controller.createDraft(new ConfigDraftRequest("demo", "order-scene", "v1", "base", "admin"));

        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(controller.validate("demo", "order-scene", "v1").valid()).isTrue();

        ConfigVersionInfo published = controller.publish("demo", "order-scene", "v1", new ConfigVersionActionRequest("admin", null, null, List.of("CONFIG_PUBLISHER")));
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
    void exposesDiffAndDryRunContracts() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-base", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        )));
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-next", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询增强")),
                null,
                null,
                null,
                null,
                null
        )));

        assertThat(controller.diff("demo", "order-scene", "v-base", "v-next").entries())
                .anySatisfy(entry -> {
                    assertThat(entry.objectType()).isEqualTo("INTENT");
                    assertThat(entry.objectId()).isEqualTo("ORDER_QUERY");
                    assertThat(entry.changeType()).isEqualTo("MODIFIED");
                });
        assertThat(controller.dryRunPublish("demo", "order-scene", "v-next", "v-base").gitOpsFiles())
                .contains("config/demo/order-scene/v-next/version.json");
    }

    @Test
    void exposesReviewApprovalAndGitOpsContracts() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-base", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        )));
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-next", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询增强")),
                null,
                null,
                null,
                null,
                null
        )));

        assertThat(controller.submitReview("demo", "order-scene", "v-next", new ConfigVersionActionRequest("reviewer")).status())
                .isEqualTo("REVIEWING");
        assertThat(controller.exportGitOps("demo", "order-scene", "v-next", "v-base", "reviewer").content())
                .containsKey("config/demo/order-scene/v-next/dry-run.json");
        assertThatThrownBy(() -> controller.approve("demo", "order-scene", "v-next", new ConfigVersionActionRequest("operator", null, null, List.of("CONFIG_OPERATOR"))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("requires role CONFIG_APPROVER");
        assertThat(controller.approve("demo", "order-scene", "v-next", new ConfigVersionActionRequest("approver", null, null, List.of("CONFIG_APPROVER"))).status())
                .isEqualTo("APPROVED");
        assertThat(controller.getVersion("demo", "order-scene", "v-next").approvedSnapshotHash()).isNotBlank();
        assertThat(controller.getVersion("demo", "order-scene", "v-next").approvedBy()).isEqualTo("approver");
        assertThat(controller.getVersion("demo", "order-scene", "v-next").approvedAt()).isNotNull();
        assertThat(controller.getVersion("demo", "order-scene", "v-next").currentSnapshotHash())
                .isEqualTo(controller.getVersion("demo", "order-scene", "v-next").approvedSnapshotHash());
        assertThatThrownBy(() -> controller.publish("demo", "order-scene", "v-next", new ConfigVersionActionRequest("publisher", null, "stale-hash", List.of("CONFIG_PUBLISHER"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected snapshot hash does not match current config snapshot");
        assertThatThrownBy(() -> controller.publish(
                "demo",
                "order-scene",
                "v-next",
                new ConfigVersionActionRequest("approver", null, controller.getVersion("demo", "order-scene", "v-next").currentSnapshotHash(), List.of("CONFIG_APPROVER"))
        ))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("requires role CONFIG_PUBLISHER");
        assertThat(controller.publish(
                "demo",
                "order-scene",
                "v-next",
                new ConfigVersionActionRequest("publisher", null, controller.getVersion("demo", "order-scene", "v-next").currentSnapshotHash(), List.of("CONFIG_PUBLISHER"))
        ).status()).isEqualTo("PUBLISHED");
        assertThat(controller.listVersionAudits("demo", "order-scene", "v-next", 10))
                .anySatisfy(entry -> {
                    assertThat(entry.action()).isEqualTo("CONFIG_APPROVED");
                    assertThat(entry.detail()).containsKey("snapshotHash");
                });
    }

    @Test
    void mapsPermissionFailureToForbiddenHttpResponse() throws Exception {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-forbidden", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "order query")),
                null,
                null,
                null,
                null,
                null
        )));
        controller.submitReview("demo", "order-scene", "v-forbidden", new ConfigVersionActionRequest("reviewer"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/admin/config/versions/v-forbidden/approve")
                        .param("tenantId", "demo")
                        .param("sceneId", "order-scene")
                        .contentType("application/json")
                        .content("""
                                {
                                  "actor": "operator",
                                  "roles": ["CONFIG_OPERATOR"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("approve config version requires role CONFIG_APPROVER"));
    }

    @Test
    void prefersAdminRequestContextHeadersForActorAndRoles() throws Exception {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-header-approve", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "order query")),
                null,
                null,
                null,
                null,
                null
        )));
        controller.submitReview("demo", "order-scene", "v-header-approve", new ConfigVersionActionRequest("reviewer"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/admin/config/versions/v-header-approve/approve")
                        .param("tenantId", "demo")
                        .param("sceneId", "order-scene")
                        .header("X-IntentHub-Actor", "iam-approver")
                        .header("X-IntentHub-Roles", "CONFIG_OPERATOR, CONFIG_APPROVER")
                        .contentType("application/json")
                        .content("""
                                {
                                  "actor": "body-operator",
                                  "roles": ["CONFIG_OPERATOR"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value("iam-approver"));
    }

    @Test
    void usesAdminRequestContextRolesForReviewWorkspace() throws Exception {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-header-workspace", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "order query")),
                null,
                null,
                null,
                null,
                null
        )));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/admin/config/versions/v-header-workspace/review-workspace")
                        .param("tenantId", "demo")
                        .param("sceneId", "order-scene")
                        .header("X-IntentHub-Roles", "CONFIG_PUBLISHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions").isArray())
                .andExpect(jsonPath("$.availableActions[?(@ == 'PUBLISH_COMPAT')]").exists());
    }

    @Test
    void exposesReviewRejectAndCancelContracts() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-reject", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        )));
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-cancel", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_CANCEL", "intentName", "订单取消")),
                null,
                null,
                null,
                null,
                null
        )));

        controller.submitReview("demo", "order-scene", "v-reject", new ConfigVersionActionRequest("reviewer"));
        assertThat(controller.rejectReview("demo", "order-scene", "v-reject", new ConfigVersionActionRequest("reviewer", "missing slots", null, List.of("CONFIG_APPROVER"))).status())
                .isEqualTo("DRAFT");

        controller.submitReview("demo", "order-scene", "v-cancel", new ConfigVersionActionRequest("reviewer"));
        controller.approve("demo", "order-scene", "v-cancel", new ConfigVersionActionRequest("approver", null, null, List.of("CONFIG_APPROVER")));
        assertThat(controller.cancelReview("demo", "order-scene", "v-cancel", new ConfigVersionActionRequest("approver", "scope changed", null, List.of("CONFIG_APPROVER"))).status())
                .isEqualTo("DRAFT");
    }

    @Test
    void exposesReviewWorkspaceForAdminPortal() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "source", "PUBLISHED", "from file", "ops", Instant.now(), Instant.now());
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-base", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        )));
        controller.importBundle(new ConfigImportRequest("demo", "order-scene", "v-next", "admin", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询增强")),
                null,
                null,
                null,
                null,
                null
        )));

        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", null).availableActions())
                .contains("VIEW_DIFF", "DRY_RUN", "EXPORT_GITOPS", "SUBMIT_REVIEW")
                .doesNotContain("PUBLISH_COMPAT");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", null).blockedReasons())
                .contains("PUBLISH_COMPAT requires role CONFIG_PUBLISHER");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER")).availableActions())
                .contains("PUBLISH_COMPAT");

        controller.submitReview("demo", "order-scene", "v-next", new ConfigVersionActionRequest("reviewer"));

        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_APPROVER")).availableActions())
                .contains("APPROVE")
                .contains("REJECT_REVIEW", "CANCEL_REVIEW")
                .doesNotContain("PUBLISH");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_APPROVER")).blockedReasons())
                .contains("REVIEWING config version must be approved before publish");

        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", null).availableActions())
                .doesNotContain("APPROVE", "REJECT_REVIEW", "CANCEL_REVIEW");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", null).blockedReasons())
                .contains("APPROVE requires role CONFIG_APPROVER");

        controller.approve("demo", "order-scene", "v-next", new ConfigVersionActionRequest("approver", null, null, List.of("CONFIG_APPROVER")));

        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER", "CONFIG_APPROVER")).availableActions())
                .contains("PUBLISH", "CANCEL_APPROVAL");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_APPROVER")).availableActions())
                .doesNotContain("PUBLISH")
                .contains("CANCEL_APPROVAL");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER")).availableActions())
                .contains("PUBLISH")
                .doesNotContain("CANCEL_APPROVAL");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER", "CONFIG_APPROVER")).version().approvedSnapshotHash()).isNotBlank();
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER", "CONFIG_APPROVER")).version().approvedBy()).isEqualTo("approver");
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER", "CONFIG_APPROVER")).version().approvedAt()).isNotNull();
        assertThat(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER", "CONFIG_APPROVER")).version().currentSnapshotHash())
                .isEqualTo(controller.reviewWorkspace("demo", "order-scene", "v-next", "v-base", List.of("CONFIG_PUBLISHER", "CONFIG_APPROVER")).version().approvedSnapshotHash());
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
        public void updateStatus(String tenantId, String sceneId, String version, String status, String actor) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            ConfigVersionInfo current = bundle.version();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(
                    new ConfigVersionInfo(current.tenantId(), current.sceneId(), current.version(), status, current.description(), current.createdBy(), current.createdAt(), current.publishedAt(), current.approvedSnapshotHash(), current.currentSnapshotHash()),
                    bundle.intents(), bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions()
            ));
        }

        @Override
        public void updateApprovedSnapshotHash(String tenantId, String sceneId, String version, String snapshotHash, String actor) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            ConfigVersionInfo current = bundle.version();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(
                    new ConfigVersionInfo(current.tenantId(), current.sceneId(), current.version(), current.status(), current.description(), current.createdBy(), current.createdAt(), current.publishedAt(), actor, Instant.now(), snapshotHash, current.currentSnapshotHash()),
                    bundle.intents(), bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions()
            ));
        }

        @Override
        public void publish(String tenantId, String sceneId, String version, String actor) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            ConfigVersionInfo current = bundle.version();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(
                    new ConfigVersionInfo(current.tenantId(), current.sceneId(), current.version(), "PUBLISHED", current.description(), current.createdBy(), current.createdAt(), Instant.now(), current.approvedBy(), current.approvedAt(), current.approvedSnapshotHash(), current.currentSnapshotHash()),
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
            List<Map<String, Object>> updated = new java.util.ArrayList<>(listFrom(bundle, type));
            String objectId = objectId(type, payload);
            updated.removeIf(item -> objectId.equals(objectId(type, item)));
            updated.add(payload);
            bundles.put(key(tenantId, sceneId, version), replaceList(bundle, type, updated));
            return payload;
        }

        @Override
        public java.util.List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type) {
            return listFrom(bundles.get(key(tenantId, sceneId, version)), type);
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

        private String objectId(ConfigObjectType type, Map<String, Object> payload) {
            return switch (type) {
                case INTENT -> payload.get("intentCode").toString();
                case SLOT -> payload.get("intentCode") + "." + payload.get("slotCode");
                case SYNONYM -> payload.get("term").toString();
                case STRATEGY -> payload.get("strategyCode").toString();
                case ROUTE -> payload.get("routeStage") + "." + payload.get("routeTarget");
                case DOWNSTREAM_ACTION -> payload.get("actionCode").toString();
            };
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
