package com.intenthub.application.config;

import java.util.List;

public record ConfigReviewWorkspace(
        ConfigVersionInfo version,
        ConfigValidationResult validation,
        ConfigDryRunReport dryRun,
        List<AuditLogEntry> audits,
        List<String> availableActions,
        List<String> blockedReasons
) {
    public ConfigReviewWorkspace {
        audits = audits == null ? List.of() : List.copyOf(audits);
        availableActions = availableActions == null ? List.of() : List.copyOf(availableActions);
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
    }
}
