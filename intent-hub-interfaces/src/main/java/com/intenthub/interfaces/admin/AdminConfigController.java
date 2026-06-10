package com.intenthub.interfaces.admin;

import com.intenthub.application.config.AuditLogEntry;
import com.intenthub.application.config.ConfigBundle;
import com.intenthub.application.config.ConfigAuditAppService;
import com.intenthub.application.config.ConfigObjectAppService;
import com.intenthub.application.config.ConfigObjectType;
import com.intenthub.application.config.ConfigDiffResult;
import com.intenthub.application.config.ConfigDryRunReport;
import com.intenthub.application.config.ConfigGitOpsExport;
import com.intenthub.application.config.ConfigReviewWorkspace;
import com.intenthub.application.config.ConfigReviewWorkspaceAppService;
import com.intenthub.application.config.ConfigValidationResult;
import com.intenthub.application.config.ConfigVersionAppService;
import com.intenthub.application.config.ConfigVersionInfo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/config")
public class AdminConfigController {
    private final ConfigVersionAppService configVersionAppService;
    private final ConfigObjectAppService configObjectAppService;
    private final ConfigAuditAppService configAuditAppService;
    private final ConfigReviewWorkspaceAppService configReviewWorkspaceAppService;

    public AdminConfigController(
            ConfigVersionAppService configVersionAppService,
            ConfigObjectAppService configObjectAppService,
            ConfigAuditAppService configAuditAppService,
            ConfigReviewWorkspaceAppService configReviewWorkspaceAppService
    ) {
        this.configVersionAppService = configVersionAppService;
        this.configObjectAppService = configObjectAppService;
        this.configAuditAppService = configAuditAppService;
        this.configReviewWorkspaceAppService = configReviewWorkspaceAppService;
    }

    @PostMapping("/versions")
    public ConfigVersionInfo createDraft(@Valid @RequestBody ConfigDraftRequest request) {
        return configVersionAppService.createDraft(
                request.tenantId(),
                request.sceneId(),
                request.version(),
                request.description(),
                request.normalizedActor()
        );
    }

    @GetMapping("/versions/{version}")
    public ConfigVersionInfo getVersion(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(required = false) List<String> roles
    ) {
        return configVersionAppService.get(tenantId, sceneId, version, AdminRequestContext.roles(roles));
    }

    public ConfigVersionInfo getVersion(String tenantId, String sceneId, String version) {
        return configVersionAppService.get(tenantId, sceneId, version);
    }

    @PostMapping("/versions/{version}/validate")
    public ConfigValidationResult validate(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(required = false) List<String> roles
    ) {
        return configVersionAppService.validate(tenantId, sceneId, version, AdminRequestContext.roles(roles));
    }

    public ConfigValidationResult validate(String tenantId, String sceneId, String version) {
        return configVersionAppService.validate(tenantId, sceneId, version);
    }

    @GetMapping("/versions/{version}/diff")
    public ConfigDiffResult diff(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @RequestParam String fromVersion,
            @PathVariable String version,
            @RequestParam(required = false) List<String> roles
    ) {
        return configVersionAppService.diff(tenantId, sceneId, fromVersion, version, AdminRequestContext.roles(roles));
    }

    public ConfigDiffResult diff(String tenantId, String sceneId, String fromVersion, String version) {
        return configVersionAppService.diff(tenantId, sceneId, fromVersion, version);
    }

    @PostMapping("/versions/{version}/dry-run")
    public ConfigDryRunReport dryRunPublish(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(required = false) String baseVersion,
            @RequestParam(required = false) List<String> roles
    ) {
        return configVersionAppService.dryRunPublish(tenantId, sceneId, version, baseVersion, AdminRequestContext.roles(roles));
    }

    public ConfigDryRunReport dryRunPublish(String tenantId, String sceneId, String version, String baseVersion) {
        return configVersionAppService.dryRunPublish(tenantId, sceneId, version, baseVersion);
    }

    @PostMapping("/versions/{version}/submit-review")
    public ConfigVersionInfo submitReview(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = request == null ? "system" : request.normalizedActor();
        return configVersionAppService.submitReview(tenantId, sceneId, version, actor);
    }

    @PostMapping("/versions/{version}/approve")
    public ConfigVersionInfo approve(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = AdminRequestContext.actor(request);
        List<String> roles = AdminRequestContext.roles(request);
        return configVersionAppService.approve(tenantId, sceneId, version, actor, roles);
    }

    @PostMapping("/versions/{version}/reject-review")
    public ConfigVersionInfo rejectReview(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = AdminRequestContext.actor(request);
        String reason = request == null ? "not provided" : request.normalizedReason();
        List<String> roles = AdminRequestContext.roles(request);
        return configVersionAppService.rejectReview(tenantId, sceneId, version, actor, reason, roles);
    }

    @PostMapping("/versions/{version}/cancel-review")
    public ConfigVersionInfo cancelReview(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = AdminRequestContext.actor(request);
        String reason = request == null ? "not provided" : request.normalizedReason();
        List<String> roles = AdminRequestContext.roles(request);
        return configVersionAppService.cancelReview(tenantId, sceneId, version, actor, reason, roles);
    }

    @PostMapping("/versions/{version}/publish")
    public ConfigVersionInfo publish(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = AdminRequestContext.actor(request);
        String expectedSnapshotHash = request == null ? null : request.normalizedExpectedSnapshotHash();
        List<String> roles = AdminRequestContext.roles(request);
        return configVersionAppService.publish(tenantId, sceneId, version, actor, expectedSnapshotHash, roles);
    }

    @PostMapping("/versions/{version}/rollback")
    public ConfigVersionInfo rollback(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = request == null ? "system" : request.normalizedActor();
        return configVersionAppService.rollback(tenantId, sceneId, version, actor);
    }

    @GetMapping("/versions/{version}/export")
    public ConfigBundle exportBundle(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(defaultValue = "system") String actor,
            @RequestParam(required = false) List<String> roles
    ) {
        return configVersionAppService.exportBundle(tenantId, sceneId, version, AdminRequestContext.actor(actor), AdminRequestContext.roles(roles));
    }

    public ConfigBundle exportBundle(String tenantId, String sceneId, String version, String actor) {
        return configVersionAppService.exportBundle(tenantId, sceneId, version, actor);
    }

    @GetMapping("/versions/{version}/audits")
    public List<AuditLogEntry> listVersionAudits(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) List<String> roles
    ) {
        return configAuditAppService.listVersionAudits(tenantId, sceneId, version, limit, AdminRequestContext.roles(roles));
    }

    public List<AuditLogEntry> listVersionAudits(String tenantId, String sceneId, String version, Integer limit) {
        return configAuditAppService.listVersionAudits(tenantId, sceneId, version, limit);
    }

    @GetMapping("/versions/{version}/gitops")
    public ConfigGitOpsExport exportGitOps(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(required = false) String baseVersion,
            @RequestParam(defaultValue = "system") String actor,
            @RequestParam(required = false) List<String> roles
    ) {
        return configVersionAppService.exportGitOps(tenantId, sceneId, version, baseVersion, AdminRequestContext.actor(actor), AdminRequestContext.roles(roles));
    }

    public ConfigGitOpsExport exportGitOps(String tenantId, String sceneId, String version, String baseVersion, String actor) {
        return configVersionAppService.exportGitOps(tenantId, sceneId, version, baseVersion, actor);
    }

    @GetMapping("/versions/{version}/review-workspace")
    public ConfigReviewWorkspace reviewWorkspace(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(required = false) String baseVersion,
            @RequestParam(required = false) List<String> roles
    ) {
        return configReviewWorkspaceAppService.getWorkspace(
                tenantId,
                sceneId,
                version,
                baseVersion,
                AdminRequestContext.roles(roles)
        );
    }

    @PostMapping("/versions/import")
    public ConfigVersionInfo importBundle(@Valid @RequestBody ConfigImportRequest request) {
        return configVersionAppService.importBundle(
                request.tenantId(),
                request.sceneId(),
                request.version(),
                request.bundle(),
                request.normalizedActor()
        );
    }

    @PostMapping("/versions/{version}/{objectType}")
    public Map<String, Object> upsertConfigObject(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @PathVariable String objectType,
            @RequestBody ConfigObjectRequest request
    ) {
        ConfigObjectRequest normalized = request == null ? new ConfigObjectRequest("system", Map.of()) : request;
        String actor = AdminRequestContext.actor(normalized.normalizedActor());
        List<String> roles = AdminRequestContext.roles(normalized.normalizedRoles());
        return configObjectAppService.upsert(
                tenantId,
                sceneId,
                version,
                ConfigObjectType.fromPath(objectType),
                normalized.payload(),
                actor,
                roles
        );
    }

    @PostMapping("/versions/{version}/{objectType}/bulk")
    public List<Map<String, Object>> bulkUpsertConfigObjects(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @PathVariable String objectType,
            @RequestBody ConfigObjectBulkRequest request
    ) {
        ConfigObjectBulkRequest normalized = request == null ? new ConfigObjectBulkRequest("system", List.of()) : request;
        String actor = AdminRequestContext.actor(normalized.normalizedActor());
        List<String> roles = AdminRequestContext.roles(normalized.normalizedRoles());
        return configObjectAppService.bulkUpsert(
                tenantId,
                sceneId,
                version,
                ConfigObjectType.fromPath(objectType),
                normalized.payloads(),
                actor,
                roles
        );
    }

    @GetMapping("/versions/{version}/{objectType}")
    public List<Map<String, Object>> listConfigObjects(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @PathVariable String objectType,
            @RequestParam(required = false) List<String> roles
    ) {
        return configObjectAppService.list(
                tenantId,
                sceneId,
                version,
                ConfigObjectType.fromPath(objectType),
                AdminRequestContext.roles(roles)
        );
    }

    public List<Map<String, Object>> listConfigObjects(String tenantId, String sceneId, String version, String objectType) {
        return configObjectAppService.list(
                tenantId,
                sceneId,
                version,
                ConfigObjectType.fromPath(objectType)
        );
    }

    @DeleteMapping("/versions/{version}/{objectType}/{objectId}")
    public Map<String, Object> deleteConfigObject(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @PathVariable String objectType,
            @PathVariable String objectId,
            @RequestParam(defaultValue = "system") String actor,
            @RequestParam(required = false) List<String> roles
    ) {
        boolean deleted = configObjectAppService.delete(
                tenantId,
                sceneId,
                version,
                ConfigObjectType.fromPath(objectType),
                objectId,
                AdminRequestContext.actor(actor),
                AdminRequestContext.roles(roles)
        );
        return Map.of("deleted", deleted);
    }
}
