package com.intenthub.interfaces.admin;

public record ConfigVersionActionRequest(
        String actor
) {
    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }
}
