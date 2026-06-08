package com.intenthub.domain.recognition.policy;

public record ModelServiceHealth(boolean healthy, String modelVersion, Double threshold) {
    public static ModelServiceHealth down() {
        return new ModelServiceHealth(false, null, null);
    }
}
