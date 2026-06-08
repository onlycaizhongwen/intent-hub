package com.intenthub.interfaces.admin;

import java.util.List;
import java.util.Map;

public record ConfigObjectBulkRequest(
        String actor,
        List<Map<String, Object>> payloads
) {
    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }
}
