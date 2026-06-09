package com.intenthub.application.config;

import java.util.List;
import java.util.Map;

public record ConfigGitOpsExport(
        String tenantId,
        String sceneId,
        String version,
        String baseVersion,
        List<String> files,
        Map<String, Object> content
) {
    public ConfigGitOpsExport {
        files = files == null ? List.of() : List.copyOf(files);
        content = content == null ? Map.of() : Map.copyOf(content);
    }
}
