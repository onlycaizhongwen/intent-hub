package com.intenthub.application.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigReviewWorkspaceAppService {
    private static final int AUDIT_LIMIT = 50;

    private final ConfigVersionAppService configVersionAppService;
    private final ConfigAuditAppService configAuditAppService;
    private final AuditLogPort auditLogPort;

    public ConfigReviewWorkspaceAppService(
            ConfigVersionAppService configVersionAppService,
            ConfigAuditAppService configAuditAppService
    ) {
        this(configVersionAppService, configAuditAppService, null);
    }

    public ConfigReviewWorkspaceAppService(
            ConfigVersionAppService configVersionAppService,
            ConfigAuditAppService configAuditAppService,
            AuditLogPort auditLogPort
    ) {
        this.configVersionAppService = configVersionAppService;
        this.configAuditAppService = configAuditAppService;
        this.auditLogPort = auditLogPort;
    }

    public ConfigReviewWorkspace getWorkspace(String tenantId, String sceneId, String version, String baseVersion) {
        return getWorkspace(tenantId, sceneId, version, baseVersion, null);
    }

    public ConfigReviewWorkspace getWorkspace(String tenantId, String sceneId, String version, String baseVersion, List<String> roles) {
        ConfigPermission.requireViewer(roles, tenantId, sceneId, "get config review workspace", auditLogPort);
        ConfigVersionInfo info = configVersionAppService.get(tenantId, sceneId, version);
        ConfigValidationResult validation = configVersionAppService.validate(tenantId, sceneId, version);
        ConfigDryRunReport dryRun = configVersionAppService.dryRunPublish(tenantId, sceneId, version, baseVersion);
        List<AuditLogEntry> audits = configAuditAppService.listVersionAudits(tenantId, sceneId, version, AUDIT_LIMIT);
        List<String> blockedReasons = blockedReasons(info, validation);
        List<String> availableActions = availableActions(info, validation, blockedReasons);
        if (roles != null) {
            availableActions = filterActionsByRole(availableActions, roles, blockedReasons, info);
        }
        return new ConfigReviewWorkspace(
                info,
                validation,
                dryRun,
                audits,
                reviewHistory(audits),
                availableActions,
                blockedReasons
        );
    }

    private List<ConfigReviewHistoryEntry> reviewHistory(List<AuditLogEntry> audits) {
        return audits.stream()
                .filter(entry -> stage(entry) != null)
                .map(this::historyEntry)
                .toList();
    }

    private ConfigReviewHistoryEntry historyEntry(AuditLogEntry entry) {
        Map<String, String> detail = entry.detail() == null ? Map.of() : entry.detail();
        return new ConfigReviewHistoryEntry(
                entry.id(),
                stage(entry),
                entry.action(),
                entry.actor(),
                status(entry, detail),
                detail.get("reason"),
                detail.get("snapshotHash"),
                detail.get("requiredRole"),
                detail.get("alternativeRole"),
                detail.get("objectType"),
                entry.createdAt(),
                detail
        );
    }

    private String stage(AuditLogEntry entry) {
        return switch (entry.action()) {
            case "CONFIG_REVIEW_SUBMITTED" -> "REVIEW_SUBMITTED";
            case "CONFIG_APPROVED" -> "APPROVED";
            case "CONFIG_REVIEW_REJECTED" -> "REJECTED";
            case "CONFIG_REVIEW_CANCELLED" -> "CANCELLED";
            case "CONFIG_PUBLISHED" -> "PUBLISHED";
            case "CONFIG_PERMISSION_DENIED" -> "PERMISSION_DENIED";
            default -> null;
        };
    }

    private String status(AuditLogEntry entry, Map<String, String> detail) {
        return switch (entry.action()) {
            case "CONFIG_REVIEW_SUBMITTED" -> "REVIEWING";
            case "CONFIG_APPROVED" -> "APPROVED";
            case "CONFIG_REVIEW_REJECTED", "CONFIG_REVIEW_CANCELLED" -> "DRAFT";
            case "CONFIG_PUBLISHED" -> "PUBLISHED";
            default -> detail.get("status");
        };
    }

    private List<String> availableActions(ConfigVersionInfo info, ConfigValidationResult validation, List<String> blockedReasons) {
        List<String> actions = new ArrayList<>();
        actions.add("VIEW_DIFF");
        actions.add("DRY_RUN");
        actions.add("EXPORT_GITOPS");
        if (!validation.valid()) {
            return actions;
        }
        switch (info.status()) {
            case "DRAFT" -> {
                actions.add("SUBMIT_REVIEW");
                actions.add("PUBLISH_COMPAT");
            }
            case "REVIEWING" -> {
                actions.add("APPROVE");
                actions.add("REJECT_REVIEW");
                actions.add("CANCEL_REVIEW");
            }
            case "APPROVED" -> {
                actions.add("PUBLISH");
                actions.add("CANCEL_APPROVAL");
            }
            case "PUBLISHED" -> actions.add("ROLLBACK_TARGET");
            default -> {
                if (blockedReasons.isEmpty()) {
                    actions.add("VIEW_ONLY");
                }
            }
        }
        return actions;
    }

    private List<String> filterActionsByRole(List<String> actions, List<String> roles, List<String> blockedReasons, ConfigVersionInfo info) {
        List<String> filtered = new ArrayList<>();
        for (String action : actions) {
            String requiredRole = requiredRole(action);
            if (requiredRole == null || ConfigRoleMatcher.hasRole(roles, requiredRole, info.tenantId(), info.sceneId())) {
                filtered.add(action);
            } else {
                blockedReasons.add(action + " requires role " + ConfigRoleMatcher.requiredRoleHint(requiredRole, info.tenantId(), info.sceneId()));
            }
        }
        return filtered;
    }

    private String requiredRole(String action) {
        return switch (action) {
            case "APPROVE", "REJECT_REVIEW", "CANCEL_REVIEW", "CANCEL_APPROVAL" -> ConfigPermission.APPROVER;
            case "PUBLISH", "PUBLISH_COMPAT" -> ConfigPermission.PUBLISHER;
            default -> null;
        };
    }

    private List<String> blockedReasons(ConfigVersionInfo info, ConfigValidationResult validation) {
        List<String> reasons = new ArrayList<>();
        if (!validation.valid()) {
            reasons.addAll(validation.errors());
        }
        if ("REVIEWING".equals(info.status())) {
            reasons.add("REVIEWING config version must be approved before publish");
        }
        if ("ARCHIVED".equals(info.status())) {
            reasons.add("ARCHIVED config version is read-only");
        }
        return reasons;
    }
}
