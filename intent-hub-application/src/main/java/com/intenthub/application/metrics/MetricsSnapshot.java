package com.intenthub.application.metrics;

import java.time.Instant;
import java.util.Map;

public record MetricsSnapshot(
        long totalRequests,
        long totalBadCases,
        long totalLlmFallbacks,
        long totalLatencyMillis,
        double averageLatencyMillis,
        long maxLatencyMillis,
        Map<String, Long> decisions,
        Map<String, Long> intents,
        Map<String, Long> scenes,
        Instant startedAt,
        Instant updatedAt
) {
    public MetricsSnapshot {
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
        intents = intents == null ? Map.of() : Map.copyOf(intents);
        scenes = scenes == null ? Map.of() : Map.copyOf(scenes);
    }
}
