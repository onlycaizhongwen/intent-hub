package com.intenthub.interfaces.admin;

import java.util.Map;
import java.util.List;

public record ConfigObjectRequest(
        String actor,
        List<String> roles,
        Map<String, Object> payload
) {
    public ConfigObjectRequest(String actor, Map<String, Object> payload) {
        this(actor, null, payload);
    }

    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    List<String> normalizedRoles() {
        return roles == null ? List.of() : roles;
    }
}
