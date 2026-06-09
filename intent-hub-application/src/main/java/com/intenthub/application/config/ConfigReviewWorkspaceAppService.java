package com.intenthub.application.config;

import java.util.ArrayList;
import java.util.List;

public class ConfigReviewWorkspaceAppService {
    private static final int AUDIT_LIMIT = 50;
    private static final String ROLE_APPROVER = "CONFIG_APPROVER";
    private static final String ROLE_PUBLISHER = "CONFIG_PUBLISHER";

    private final ConfigVersionAppService configVersionAppService;
    private final ConfigAuditAppService configAuditAppService;

    public ConfigReviewWorkspaceAppService(
            ConfigVersionAppService configVersionAppService,
            ConfigAuditAppService configAuditAppService
    ) {
        this.configVersionAppService = configVersionAppService;
        this.configAuditAppService = configAuditAppService;
    }

    public ConfigReviewWorkspace getWorkspace(String tenantId, String sceneId, String version, String baseVersion) {
        return getWorkspace(tenantId, sceneId, version, baseVersion, null);
    }

    public ConfigReviewWorkspace getWorkspace(String tenantId, String sceneId, String version, String baseVersion, List<String> roles) {
        ConfigVersionInfo info = configVersionAppService.get(tenantId, sceneId, version);
        ConfigValidationResult validation = configVersionAppService.validate(tenantId, sceneId, version);
        ConfigDryRunReport dryRun = configVersionAppService.dryRunPublish(tenantId, sceneId, version, baseVersion);
        List<AuditLogEntry> audits = configAuditAppService.listVersionAudits(tenantId, sceneId, version, AUDIT_LIMIT);
        List<String> blockedReasons = blockedReasons(info, validation);
        List<String> availableActions = availableActions(info, validation, blockedReasons);
        if (roles != null) {
            availableActions = filterActionsByRole(availableActions, roles, blockedReasons);
        }
        return new ConfigReviewWorkspace(
                info,
                validation,
                dryRun,
                audits,
                availableActions,
                blockedReasons
        );
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

    private List<String> filterActionsByRole(List<String> actions, List<String> roles, List<String> blockedReasons) {
        List<String> filtered = new ArrayList<>();
        for (String action : actions) {
            String requiredRole = requiredRole(action);
            if (requiredRole == null || hasRole(roles, requiredRole)) {
                filtered.add(action);
            } else {
                blockedReasons.add(action + " requires role " + requiredRole);
            }
        }
        return filtered;
    }

    private String requiredRole(String action) {
        return switch (action) {
            case "APPROVE", "REJECT_REVIEW", "CANCEL_REVIEW", "CANCEL_APPROVAL" -> ROLE_APPROVER;
            case "PUBLISH", "PUBLISH_COMPAT" -> ROLE_PUBLISHER;
            default -> null;
        };
    }

    private boolean hasRole(List<String> roles, String requiredRole) {
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .anyMatch(requiredRole::equals);
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
