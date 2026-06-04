package com.intenthub.application.metrics;

public record MetricsAlert(
        String code,
        AlertSeverity severity,
        String message,
        double observedValue,
        double threshold
) {
}
