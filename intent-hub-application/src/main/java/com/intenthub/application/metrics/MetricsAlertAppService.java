package com.intenthub.application.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MetricsAlertAppService {
    private static final double BAD_CASE_RATE_WARN_THRESHOLD = 0.30;
    private static final double AVG_LATENCY_WARN_MILLIS = 1000.0;
    private static final double P95_LATENCY_WARN_MILLIS = 1500.0;
    private static final double P99_LATENCY_CRITICAL_MILLIS = 3000.0;
    private static final double MAX_LATENCY_CRITICAL_MILLIS = 3000.0;

    private final IntentMetricsPort metricsPort;

    public MetricsAlertAppService(IntentMetricsPort metricsPort) {
        this.metricsPort = metricsPort;
    }

    public MetricsAlertSnapshot snapshot() {
        MetricsSnapshot metrics = metricsPort.snapshot();
        List<MetricsAlert> alerts = new ArrayList<>();
        addBadCaseRateAlert(metrics, alerts);
        addPositiveCounterAlert(alerts, "MODEL_FALLBACK", AlertSeverity.WARN,
                "Model service fallback has occurred.", metrics.totalModelFallbacks());
        addPositiveCounterAlert(alerts, "LLM_FALLBACK", AlertSeverity.WARN,
                "LLM fallback has occurred.", metrics.totalLlmFallbacks());
        addPositiveCounterAlert(alerts, "LLM_BUDGET_RECONCILIATION", AlertSeverity.WARN,
                "Stale LLM budget reservations were reconciled.", metrics.totalLlmBudgetReconciliations());
        addPositiveCounterAlert(alerts, "CONFIG_PERMISSION_DENIED", AlertSeverity.WARN,
                "Config permission denials have occurred.", metrics.totalPermissionDenied());
        addPositiveCounterAlert(alerts, "ADMIN_JWT_AUTH_FAILED", AlertSeverity.WARN,
                "Admin JWT authentication failures have occurred.", metrics.totalAdminJwtAuthFailures());
        if (metrics.averageLatencyMillis() >= AVG_LATENCY_WARN_MILLIS) {
            alerts.add(new MetricsAlert("AVG_LATENCY_HIGH", AlertSeverity.WARN,
                    "Average recognition latency is above the warning threshold.",
                    metrics.averageLatencyMillis(), AVG_LATENCY_WARN_MILLIS));
        }
        if (metrics.p95LatencyMillis() >= P95_LATENCY_WARN_MILLIS) {
            alerts.add(new MetricsAlert("P95_LATENCY_HIGH", AlertSeverity.WARN,
                    "P95 recognition latency is above the warning threshold.",
                    metrics.p95LatencyMillis(), P95_LATENCY_WARN_MILLIS));
        }
        if (metrics.p99LatencyMillis() >= P99_LATENCY_CRITICAL_MILLIS) {
            alerts.add(new MetricsAlert("P99_LATENCY_HIGH", AlertSeverity.CRITICAL,
                    "P99 recognition latency is above the critical threshold.",
                    metrics.p99LatencyMillis(), P99_LATENCY_CRITICAL_MILLIS));
        }
        if (metrics.maxLatencyMillis() >= MAX_LATENCY_CRITICAL_MILLIS) {
            alerts.add(new MetricsAlert("MAX_LATENCY_HIGH", AlertSeverity.CRITICAL,
                    "Max recognition latency is above the critical threshold.",
                    metrics.maxLatencyMillis(), MAX_LATENCY_CRITICAL_MILLIS));
        }
        return new MetricsAlertSnapshot(status(alerts), alerts, Instant.now());
    }

    private void addBadCaseRateAlert(MetricsSnapshot metrics, List<MetricsAlert> alerts) {
        if (metrics.totalRequests() <= 0) {
            return;
        }
        double badCaseRate = (double) metrics.totalBadCases() / metrics.totalRequests();
        if (badCaseRate >= BAD_CASE_RATE_WARN_THRESHOLD) {
            alerts.add(new MetricsAlert("BAD_CASE_RATE_HIGH", AlertSeverity.WARN,
                    "Bad case rate is above the warning threshold.",
                    badCaseRate, BAD_CASE_RATE_WARN_THRESHOLD));
        }
    }

    private void addPositiveCounterAlert(List<MetricsAlert> alerts, String code, AlertSeverity severity,
                                         String message, long value) {
        if (value > 0) {
            alerts.add(new MetricsAlert(code, severity, message, value, 0.0));
        }
    }

    private String status(List<MetricsAlert> alerts) {
        if (alerts.stream().anyMatch(alert -> alert.severity() == AlertSeverity.CRITICAL)) {
            return "CRITICAL";
        }
        if (alerts.stream().anyMatch(alert -> alert.severity() == AlertSeverity.WARN)) {
            return "WARN";
        }
        return "OK";
    }
}
