package com.intenthub.application.config;

import java.util.List;

public record ConfigDryRunReport(
        String tenantId,
        String sceneId,
        String version,
        boolean publishable,
        ConfigValidationResult validation,
        ConfigDiffResult diff,
        List<String> gitOpsFiles
) {
    public ConfigDryRunReport {
        gitOpsFiles = gitOpsFiles == null ? List.of() : List.copyOf(gitOpsFiles);
    }
}
