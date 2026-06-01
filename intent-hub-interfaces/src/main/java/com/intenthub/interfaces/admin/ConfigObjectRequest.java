package com.intenthub.interfaces.admin;

import java.util.Map;

public record ConfigObjectRequest(
        String actor,
        Map<String, Object> payload
) {
    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }
}
