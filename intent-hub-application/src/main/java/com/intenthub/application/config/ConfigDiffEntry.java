package com.intenthub.application.config;

import java.util.Map;

public record ConfigDiffEntry(
        String objectType,
        String objectId,
        String changeType,
        Map<String, Object> before,
        Map<String, Object> after
) {
    public ConfigDiffEntry {
        before = before == null ? Map.of() : Map.copyOf(before);
        after = after == null ? Map.of() : Map.copyOf(after);
    }
}
