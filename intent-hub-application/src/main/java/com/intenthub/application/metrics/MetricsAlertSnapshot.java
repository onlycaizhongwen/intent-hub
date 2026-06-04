package com.intenthub.application.metrics;

import java.time.Instant;
import java.util.List;

public record MetricsAlertSnapshot(
        String status,
        List<MetricsAlert> alerts,
        Instant generatedAt
) {
    public MetricsAlertSnapshot {
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
    }
}
