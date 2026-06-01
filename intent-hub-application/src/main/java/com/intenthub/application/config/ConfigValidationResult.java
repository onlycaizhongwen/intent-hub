package com.intenthub.application.config;

import java.util.List;

public record ConfigValidationResult(
        boolean valid,
        List<String> errors
) {
    public ConfigValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ConfigValidationResult ok() {
        return new ConfigValidationResult(true, List.of());
    }

    public static ConfigValidationResult failed(List<String> errors) {
        return new ConfigValidationResult(false, errors);
    }
}
