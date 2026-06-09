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
    void comparesConfigVersionsByObjectIdentity() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "diff bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-base", new ConfigBundle(
                source,
                List.of(
                        Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询"),
                        Map.of("intentCode", "ORDER_CANCEL", "intentName", "订单取消")
                ),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.importBundle("demo", "order-scene", "v-next", new ConfigBundle(
                source,
                List.of(
                        Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询增强"),
                        Map.of("intentCode", "REFUND_APPLY", "intentName", "退款申请")
                ),
                null,
                null,
                null,
                null,
                null
        ), "admin");

        ConfigDiffResult diff = service.diff("demo", "order-scene", "v-base", "v-next");

        assertThat(diff.added()).isEqualTo(1);
        assertThat(diff.modified()).isEqualTo(1);
        assertThat(diff.removed()).isEqualTo(1);
        assertThat(diff.entries()).extracting(ConfigDiffEntry::objectId)
                .contains("ORDER_QUERY", "ORDER_CANCEL", "REFUND_APPLY");
        assertThat(diff.entries()).anySatisfy(entry -> {
            assertThat(entry.objectId()).isEqualTo("ORDER_QUERY");
            assertThat(entry.changeType()).isEqualTo("MODIFIED");
            assertThat(entry.before()).containsEntry("intentName", "订单查询");
            assertThat(entry.after()).containsEntry("intentName", "订单查询增强");
        });
    }

    @Test
    void dryRunPublishReturnsValidationDiffAndGitOpsFilePlan() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "dry run bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-base", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.importBundle("demo", "order-scene", "v-next", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                List.of(Map.of("intentCode", "ORDER_CANCEL", "slotCode", "orderId")),
                null,
                null,
                null,
                null
        ), "admin");

        ConfigDryRunReport report = service.dryRunPublish("demo", "order-scene", "v-next", "v-base");

        assertThat(report.publishable()).isFalse();
        assertThat(report.validation().errors()).contains("slot ORDER_CANCEL.orderId references missing intent ORDER_CANCEL");
        assertThat(report.diff()).isNotNull();
        assertThat(report.diff().added()).isEqualTo(1);
        assertThat(report.gitOpsFiles()).contains(
                "config/demo/order-scene/v-next/version.json",
                "config/demo/order-scene/v-next/intents.json",
                "config/demo/order-scene/v-next/downstream-actions.json"
        );
    }

    @Test
    void supportsReviewApprovalAndGitOpsExportBeforePublish() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "review bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-base", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.importBundle("demo", "order-scene", "v-review", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询增强")),
                null,
                null,
                null,
                null,
                null
        ), "admin");

        ConfigVersionInfo reviewing = service.submitReview("demo", "order-scene", "v-review", "reviewer");

        assertThat(reviewing.status()).isEqualTo("REVIEWING");
        assertThatThrownBy(() -> service.publish("demo", "order-scene", "v-review", "publisher"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be approved");

        ConfigGitOpsExport export = service.exportGitOps("demo", "order-scene", "v-review", "v-base", "reviewer");

        assertThat(export.files()).contains("config/demo/order-scene/v-review/version.json");
        assertThat(export.content()).containsKeys(
                "config/demo/order-scene/v-review/version.json",
                "config/demo/order-scene/v-review/dry-run.json"
        );
        assertThat(((ConfigDryRunReport) export.content().get("config/demo/order-scene/v-review/dry-run.json")).diff().modified())
                .isEqualTo(1);

        ConfigVersionInfo approved = service.approve("demo", "order-scene", "v-review", "approver");
        assertThat(approved.status()).isEqualTo("APPROVED");

        ConfigVersionInfo published = service.publish("demo", "order-scene", "v-review", "publisher", approved.currentSnapshotHash());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(auditLogPort.actions).contains(
                "CONFIG_REVIEW_SUBMITTED",
                "CONFIG_GITOPS_EXPORTED",
                "CONFIG_APPROVED",
                "CONFIG_PUBLISHED"
        );
    }

    @Test
    void enforcesReviewAndPublishRolesWhenRolesAreProvided() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "role bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-role", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.submitReview("demo", "order-scene", "v-role", "reviewer");

        assertThatThrownBy(() -> service.approve("demo", "order-scene", "v-role", "operator", List.of("CONFIG_OPERATOR")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("requires role CONFIG_APPROVER");

        ConfigVersionInfo approved = service.approve("demo", "order-scene", "v-role", "approver", List.of("CONFIG_APPROVER"));

        assertThatThrownBy(() -> service.publish("demo", "order-scene", "v-role", "operator", approved.currentSnapshotHash(), List.of("CONFIG_APPROVER")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("requires role CONFIG_PUBLISHER");

        ConfigVersionInfo published = service.publish("demo", "order-scene", "v-role", "publisher", approved.currentSnapshotHash(), List.of("CONFIG_PUBLISHER"));
        assertThat(published.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void filtersReviewWorkspaceActionsWhenRolesAreProvided() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "workspace role bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-workspace-role", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "order query")),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.submitReview("demo", "order-scene", "v-workspace-role", "reviewer");
        ConfigReviewWorkspaceAppService workspaceService = new ConfigReviewWorkspaceAppService(service, new ConfigAuditAppService(auditLogPort));

        ConfigReviewWorkspace legacyWorkspace = workspaceService.getWorkspace("demo", "order-scene", "v-workspace-role", null);
        assertThat(legacyWorkspace.availableActions()).contains("APPROVE", "REJECT_REVIEW", "CANCEL_REVIEW");

        ConfigReviewWorkspace operatorWorkspace = workspaceService.getWorkspace(
                "demo",
                "order-scene",
                "v-workspace-role",
                null,
                List.of("CONFIG_OPERATOR")
        );
        assertThat(operatorWorkspace.availableActions()).doesNotContain("APPROVE", "REJECT_REVIEW", "CANCEL_REVIEW");
        assertThat(operatorWorkspace.blockedReasons())
                .contains("APPROVE requires role CONFIG_APPROVER");

        ConfigReviewWorkspace approverWorkspace = workspaceService.getWorkspace(
                "demo",
                "order-scene",
                "v-workspace-role",
                null,
                List.of("CONFIG_APPROVER")
        );
        assertThat(approverWorkspace.availableActions()).contains("APPROVE", "REJECT_REVIEW", "CANCEL_REVIEW");
        assertThat(approverWorkspace.blockedReasons())
                .doesNotContain("APPROVE requires role CONFIG_APPROVER");
    }

    @Test
    void returnsReviewingOrApprovedVersionToDraft() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "review return bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-reject", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.importBundle("demo", "order-scene", "v-cancel-approved", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_CANCEL", "intentName", "订单取消")),
                null,
                null,
                null,
                null,
                null
        ), "admin");

        service.submitReview("demo", "order-scene", "v-reject", "reviewer");
        ConfigVersionInfo rejected = service.rejectReview("demo", "order-scene", "v-reject", "reviewer", "need more slots");

        assertThat(rejected.status()).isEqualTo("DRAFT");

        service.submitReview("demo", "order-scene", "v-cancel-approved", "reviewer");
        service.approve("demo", "order-scene", "v-cancel-approved", "approver");
        ConfigVersionInfo cancelled = service.cancelReview("demo", "order-scene", "v-cancel-approved", "approver", "scope changed");

        assertThat(cancelled.status()).isEqualTo("DRAFT");
        assertThat(auditLogPort.actions).contains("CONFIG_REVIEW_REJECTED", "CONFIG_REVIEW_CANCELLED");
    }

    @Test
    void blocksPublishWhenApprovedSnapshotChanged() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "snapshot bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-snapshot", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.submitReview("demo", "order-scene", "v-snapshot", "reviewer");
        ConfigVersionInfo approved = service.approve("demo", "order-scene", "v-snapshot", "approver");

        assertThat(approved.approvedSnapshotHash()).isNotBlank();
        assertThat(approved.approvedBy()).isEqualTo("approver");
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.currentSnapshotHash()).isEqualTo(approved.approvedSnapshotHash());

        port.upsert("demo", "order-scene", "v-snapshot", ConfigObjectType.INTENT, Map.of(
                "intentCode", "ORDER_QUERY",
                "intentName", "订单查询已漂移"
        ));

        assertThatThrownBy(() -> service.publish("demo", "order-scene", "v-snapshot", "publisher"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved config snapshot has changed");
    }

    @Test
    void blocksPublishWhenExpectedSnapshotHashDoesNotMatchCurrentSnapshot() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "expected hash bundle", "ops", Instant.now(), Instant.now());
        service.importBundle("demo", "order-scene", "v-expected", new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                null,
                null,
                null,
                null,
                null
        ), "admin");
        service.submitReview("demo", "order-scene", "v-expected", "reviewer");
        ConfigVersionInfo approved = service.approve("demo", "order-scene", "v-expected", "approver");

        assertThatThrownBy(() -> service.publish("demo", "order-scene", "v-expected", "publisher", "stale-hash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected snapshot hash does not match current config snapshot");

        ConfigVersionInfo published = service.publish("demo", "order-scene", "v-expected", "publisher", approved.currentSnapshotHash());
        assertThat(published.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void validatesCrossObjectReferencesBeforePublish() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "broken bundle", "ops", Instant.now(), Instant.now());
        ConfigBundle bundle = new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询")),
                List.of(Map.of("intentCode", "ORDER_CANCEL", "slotCode", "orderId")),
                null,
                null,
                List.of(Map.of("routeStage", "POST", "routeTarget", "MISSING_ACTION")),
                List.of(Map.of("actionCode", "ORDER_CANCEL_API", "actionType", "API", "target", "https://api.example.test"))
        );
        service.importBundle("demo", "order-scene", "v-cross-ref", bundle, "admin");

        ConfigValidationResult result = service.validate("demo", "order-scene", "v-cross-ref");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "slot ORDER_CANCEL.orderId references missing intent ORDER_CANCEL",
                "POST route MISSING_ACTION references missing downstream action",
                "downstream action ORDER_CANCEL_API references missing intent ORDER_CANCEL"
        );
        assertThatThrownBy(() -> service.publish("demo", "order-scene", "v-cross-ref", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("references missing");
    }

    @Test
    void validatesDownstreamActionExplicitIntentReference() {
        ConfigVersionInfo source = new ConfigVersionInfo("demo", "order-scene", "external", "PUBLISHED", "explicit action intent", "ops", Instant.now(), Instant.now());
        ConfigBundle valid = new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_CANCEL", "intentName", "订单取消")),
                null,
                null,
                null,
                null,
                List.of(Map.of(
                        "actionCode", "VIP_CANCEL_COMMAND",
                        "actionType", "MQ",
                        "target", "order.command.cancel.vip",
                        "actionSchema", Map.of("intentCode", "ORDER_CANCEL")
                ))
        );
        service.importBundle("demo", "order-scene", "v-explicit-action", valid, "admin");

        assertThat(service.validate("demo", "order-scene", "v-explicit-action").valid()).isTrue();

        ConfigBundle invalid = new ConfigBundle(
                source,
                List.of(Map.of("intentCode", "ORDER_CANCEL", "intentName", "订单取消")),
                null,
                null,
                null,
                null,
                List.of(Map.of(
                        "actionCode", "VIP_CANCEL_COMMAND",
                        "actionType", "MQ",
                        "target", "order.command.cancel.vip",
                        "actionSchema", Map.of("intentCode", "ORDER_REFUND")
                ))
        );
        service.importBundle("demo", "order-scene", "v-explicit-action-invalid", invalid, "admin");

        ConfigValidationResult result = service.validate("demo", "order-scene", "v-explicit-action-invalid");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("downstream action VIP_CANCEL_COMMAND references missing intent ORDER_REFUND");
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
    void bulkUpsertsAndDeletesConfigObjectsOnlyForDraftVersions() {
        service.createDraft("demo", "order-scene", "v-bulk", "bulk", "admin");
        ConfigObjectAppService objectService = new ConfigObjectAppService(port, port, auditLogPort);

        List<Map<String, Object>> saved = objectService.bulkUpsert("demo", "order-scene", "v-bulk", ConfigObjectType.INTENT, List.of(
                Map.of("intentCode", "ORDER_QUERY", "intentName", "订单查询"),
                Map.of("intentCode", "ORDER_CANCEL", "intentName", "订单取消")
        ), "admin");

        assertThat(saved).hasSize(2);
        assertThat(objectService.list("demo", "order-scene", "v-bulk", ConfigObjectType.INTENT)).hasSize(2);
        assertThat(objectService.delete("demo", "order-scene", "v-bulk", ConfigObjectType.INTENT, "ORDER_QUERY", "admin")).isTrue();
        assertThat(objectService.list("demo", "order-scene", "v-bulk", ConfigObjectType.INTENT)).extracting(item -> item.get("intentCode"))
                .containsExactly("ORDER_CANCEL");
        assertThat(auditLogPort.actions).contains("CONFIG_OBJECT_BULK_UPSERTED", "CONFIG_OBJECT_DELETED");

        service.publish("demo", "order-scene", "v-bulk", "admin");
        assertThatThrownBy(() -> objectService.delete("demo", "order-scene", "v-bulk", ConfigObjectType.INTENT, "ORDER_CANCEL", "admin"))
                .isInstanceOf(IllegalStateException.class)
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
                        "minConfidence", 0.72,
                        "authTokenRef", "INTENT_HUB_MODEL_TOKEN"
                )
        ), "admin");

        assertThat(saved).containsKey("modelPolicy");
        assertThat(saved.get("modelPolicy")).isEqualTo(Map.of(
                "enabled", false,
                "endpoint", "https://model.example.test",
                "timeoutMs", 1800,
                "minConfidence", 0.72,
                "authTokenRef", "INTENT_HUB_MODEL_TOKEN"
        ));
    }

    @Test
    void storesExplicitIntentCodeIntoDownstreamActionSchema() {
        service.createDraft("demo", "order-scene", "v-action-intent", "action intent", "admin");
        ConfigObjectAppService objectService = new ConfigObjectAppService(port, port, auditLogPort);

        Map<String, Object> saved = objectService.upsert("demo", "order-scene", "v-action-intent", ConfigObjectType.DOWNSTREAM_ACTION, Map.of(
                "actionCode", "VIP_CANCEL_COMMAND",
                "intentCode", "ORDER_CANCEL",
                "actionType", "MQ",
                "target", "order.command.cancel.vip"
        ), "admin");

        assertThat(saved.get("actionSchema")).isEqualTo(Map.of("intentCode", "ORDER_CANCEL"));
    }

    @Test
    void rejectsDownstreamActionIntentCodeMismatch() {
        service.createDraft("demo", "order-scene", "v-action-intent-mismatch", "action intent mismatch", "admin");
        ConfigObjectAppService objectService = new ConfigObjectAppService(port, port, auditLogPort);

        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v-action-intent-mismatch", ConfigObjectType.DOWNSTREAM_ACTION, Map.of(
                "actionCode", "VIP_CANCEL_COMMAND",
                "intentCode", "ORDER_CANCEL",
                "actionType", "MQ",
                "target", "order.command.cancel.vip",
                "actionSchema", Map.of("intentCode", "ORDER_QUERY")
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intentCode must match actionSchema.intentCode");
    }

    @Test
    void rejectsInvalidConfigObjectFields() {
        service.createDraft("demo", "order-scene", "v-field-validation", "field validation", "admin");
        ConfigObjectAppService objectService = new ConfigObjectAppService(port, port, auditLogPort);

        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v-field-validation", ConfigObjectType.STRATEGY, Map.of(
                "strategyCode", "invalid-confidence",
                "confidenceThreshold", 1.20
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidenceThreshold must be between 0.0 and 1.0");

        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v-field-validation", ConfigObjectType.STRATEGY, Map.of(
                "strategyCode", "invalid-model-policy",
                "modelPolicy", Map.of(
                        "minConfidence", -0.10
                )
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelPolicy.minConfidence must be between 0.0 and 1.0");

        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v-field-validation", ConfigObjectType.STRATEGY, Map.of(
                "strategyCode", "invalid-llm-policy",
                "llmPolicy", Map.of(
                        "timeoutMs", 0,
                        "maxRetries", 9,
                        "dailyBudget", -1.0
                )
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("llmPolicy.timeoutMs must be between 1 and 60000");

        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v-field-validation", ConfigObjectType.ROUTE, Map.of(
                "routeStage", "MIDDLE",
                "routeTarget", "ORDER_QUERY"
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routeStage must be one of [PRE, POST]");

        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v-field-validation", ConfigObjectType.DOWNSTREAM_ACTION, Map.of(
                "actionCode", "SQL_ACTION",
                "actionType", "SQL",
                "target", "select * from orders"
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType must be one of [API, MQ, WEBHOOK, MQTT]");

        assertThatThrownBy(() -> objectService.upsert("demo", "order-scene", "v-field-validation", ConfigObjectType.DOWNSTREAM_ACTION, Map.of(
                "actionCode", "SLOW_ACTION",
                "actionType", "API",
                "target", "https://api.example.test",
                "timeoutMs", 60001
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeoutMs must be between 1 and 60000");
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
                return withInfo(bundle, new ConfigVersionInfo(current.tenantId(), current.sceneId(), current.version(), status, current.description(), current.createdBy(), current.createdAt(), version.equals(current.version()) ? Instant.now() : current.publishedAt(), current.approvedBy(), current.approvedAt(), current.approvedSnapshotHash(), current.currentSnapshotHash()));
            });
        }

        @Override
        public void rollback(String tenantId, String sceneId, String targetVersion, String actor) {
            publish(tenantId, sceneId, targetVersion, actor);
        }

        @Override
        public Map<String, Object> upsert(String tenantId, String sceneId, String version, ConfigObjectType type, Map<String, Object> payload) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            List<Map<String, Object>> intents = type == ConfigObjectType.INTENT ? upsertByKey(bundle.intents(), payload, "intentCode") : bundle.intents();
            List<Map<String, Object>> strategies = type == ConfigObjectType.STRATEGY ? List.of(payload) : bundle.strategies();
            List<Map<String, Object>> downstreamActions = type == ConfigObjectType.DOWNSTREAM_ACTION ? upsertByKey(bundle.downstreamActions(), payload, "actionCode") : bundle.downstreamActions();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(bundle.version(), intents, bundle.slots(), bundle.synonyms(), strategies, bundle.routes(), downstreamActions));
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

        @Override
        public boolean delete(String tenantId, String sceneId, String version, ConfigObjectType type, String objectId) {
            ConfigBundle bundle = bundles.get(key(tenantId, sceneId, version));
            if (type != ConfigObjectType.INTENT || bundle.intents() == null) {
                return false;
            }
            List<Map<String, Object>> remaining = bundle.intents().stream()
                    .filter(item -> !objectId.equals(item.get("intentCode")))
                    .toList();
            bundles.put(key(tenantId, sceneId, version), new ConfigBundle(bundle.version(), remaining, bundle.slots(), bundle.synonyms(), bundle.strategies(), bundle.routes(), bundle.downstreamActions()));
            return remaining.size() != bundle.intents().size();
        }

        private List<Map<String, Object>> upsertByKey(List<Map<String, Object>> current, Map<String, Object> payload, String key) {
            List<Map<String, Object>> values = new ArrayList<>(current == null ? List.of() : current);
            values.removeIf(item -> payload.get(key).equals(item.get(key)));
            values.add(payload);
            return values;
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
