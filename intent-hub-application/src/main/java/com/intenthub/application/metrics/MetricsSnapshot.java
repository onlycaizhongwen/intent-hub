package com.intenthub.application.metrics;

import java.time.Instant;
import java.util.Map;

public record MetricsSnapshot(
        long totalRequests,
        long totalBadCases,
        long totalModelFallbacks,
        long totalLlmFallbacks,
        long totalLlmBudgetAttempts,
        double totalLlmBudgetConsumed,
        long totalLlmBudgetReconciliations,
        long totalPermissionDenied,
        long totalAdminJwtAuthFailures,
        long totalAdminJwksFetches,
        long totalAdminJwksFetchFailures,
        long totalAdminJwksCacheHits,
        long totalAdminJwksStaleHits,
        long totalAdminOidcDiscoveryFetches,
        long totalAdminOidcDiscoveryFetchFailures,
        long totalLatencyMillis,
        double averageLatencyMillis,
        long maxLatencyMillis,
        double p95LatencyMillis,
        double p99LatencyMillis,
        Map<String, Long> decisions,
        Map<String, Long> intents,
        Map<String, Long> scenes,
        Instant startedAt,
        Instant updatedAt
) {
    public MetricsSnapshot(
            long totalRequests,
            long totalBadCases,
            long totalModelFallbacks,
            long totalLlmFallbacks,
            long totalLlmBudgetAttempts,
            double totalLlmBudgetConsumed,
            long totalLlmBudgetReconciliations,
            long totalPermissionDenied,
            long totalLatencyMillis,
            double averageLatencyMillis,
            long maxLatencyMillis,
            Map<String, Long> decisions,
            Map<String, Long> intents,
            Map<String, Long> scenes,
            Instant startedAt,
            Instant updatedAt
    ) {
        this(
                totalRequests,
                totalBadCases,
                totalModelFallbacks,
                totalLlmFallbacks,
                totalLlmBudgetAttempts,
                totalLlmBudgetConsumed,
                totalLlmBudgetReconciliations,
                totalPermissionDenied,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                totalLatencyMillis,
                averageLatencyMillis,
                maxLatencyMillis,
                maxLatencyMillis,
                maxLatencyMillis,
                decisions,
                intents,
                scenes,
                startedAt,
                updatedAt
        );
    }

    public MetricsSnapshot(
            long totalRequests,
            long totalBadCases,
            long totalModelFallbacks,
            long totalLlmFallbacks,
            long totalLlmBudgetAttempts,
            double totalLlmBudgetConsumed,
            long totalLlmBudgetReconciliations,
            long totalPermissionDenied,
            long totalAdminJwtAuthFailures,
            long totalLatencyMillis,
            double averageLatencyMillis,
            long maxLatencyMillis,
            double p95LatencyMillis,
            double p99LatencyMillis,
            Map<String, Long> decisions,
            Map<String, Long> intents,
            Map<String, Long> scenes,
            Instant startedAt,
            Instant updatedAt
    ) {
        this(
                totalRequests,
                totalBadCases,
                totalModelFallbacks,
                totalLlmFallbacks,
                totalLlmBudgetAttempts,
                totalLlmBudgetConsumed,
                totalLlmBudgetReconciliations,
                totalPermissionDenied,
                totalAdminJwtAuthFailures,
                0,
                0,
                0,
                0,
                0,
                0,
                totalLatencyMillis,
                averageLatencyMillis,
                maxLatencyMillis,
                p95LatencyMillis,
                p99LatencyMillis,
                decisions,
                intents,
                scenes,
                startedAt,
                updatedAt
        );
    }

    public MetricsSnapshot {
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
        intents = intents == null ? Map.of() : Map.copyOf(intents);
        scenes = scenes == null ? Map.of() : Map.copyOf(scenes);
    }
}
