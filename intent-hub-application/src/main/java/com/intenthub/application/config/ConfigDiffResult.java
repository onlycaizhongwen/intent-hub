package com.intenthub.application.config;

import java.util.List;

public record ConfigDiffResult(
        String tenantId,
        String sceneId,
        String fromVersion,
        String toVersion,
        List<ConfigDiffEntry> entries,
        int added,
        int modified,
        int removed
) {
    public ConfigDiffResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
