package com.intenthub.interfaces.admin;

import jakarta.validation.constraints.NotBlank;

public record ConfigDraftRequest(
        @NotBlank String tenantId,
        @NotBlank String sceneId,
        @NotBlank String version,
        String description,
        String actor
) {
    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }
}
