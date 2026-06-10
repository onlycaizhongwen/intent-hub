package com.intenthub.interfaces.admin;

import java.util.List;
import java.util.Map;

public record ConfigObjectBulkRequest(
        String actor,
        List<String> roles,
        List<Map<String, Object>> payloads
) {
    public ConfigObjectBulkRequest(String actor, List<Map<String, Object>> payloads) {
        this(actor, null, payloads);
    }

    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    List<String> normalizedRoles() {
        return roles == null ? List.of() : roles;
    }
}
