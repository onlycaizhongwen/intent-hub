package com.intenthub.interfaces.admin;

import com.intenthub.application.config.ConfigBundle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfigImportRequest(
        @NotBlank String tenantId,
        @NotBlank String sceneId,
        @NotBlank String version,
        String actor,
        @NotNull ConfigBundle bundle
) {
    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }
}
