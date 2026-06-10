package com.intenthub.application.metrics;

public class MetricsAppService {
    private final IntentMetricsPort metricsPort;

    public MetricsAppService(IntentMetricsPort metricsPort) {
        this.metricsPort = metricsPort;
    }

    public MetricsSnapshot snapshot() {
        return metricsPort.snapshot();
    }

    public String prometheus() {
        MetricsSnapshot snapshot = metricsPort.snapshot();
        StringBuilder builder = new StringBuilder();
        appendGauge(builder, "intent_hub_requests_total", "Total recognized requests.", snapshot.totalRequests());
        appendGauge(builder, "intent_hub_bad_cases_total", "Total bad case candidates.", snapshot.totalBadCases());
        appendGauge(builder, "intent_hub_model_fallbacks_total", "Total model fallback closures.", snapshot.totalModelFallbacks());
        appendGauge(builder, "intent_hub_llm_fallbacks_total", "Total LLM fallback attempts.", snapshot.totalLlmFallbacks());
        appendGauge(builder, "intent_hub_llm_budget_attempts_total", "Total LLM budget-consuming attempts.", snapshot.totalLlmBudgetAttempts());
        appendGauge(builder, "intent_hub_llm_budget_consumed_total", "Total LLM budget units consumed.", snapshot.totalLlmBudgetConsumed());
        appendGauge(builder, "intent_hub_llm_budget_reconciliations_total", "Total stale LLM budget reservations reconciled.", snapshot.totalLlmBudgetReconciliations());
        appendGauge(builder, "intent_hub_permission_denied_total", "Total config permission denials.", snapshot.totalPermissionDenied());
        appendGauge(builder, "intent_hub_admin_jwt_auth_failures_total", "Total Admin JWT authentication failures.", snapshot.totalAdminJwtAuthFailures());
        appendGauge(builder, "intent_hub_latency_millis_sum", "Total recognition latency in milliseconds.", snapshot.totalLatencyMillis());
        appendGauge(builder, "intent_hub_latency_millis_avg", "Average recognition latency in milliseconds.", snapshot.averageLatencyMillis());
        appendGauge(builder, "intent_hub_latency_millis_max", "Max recognition latency in milliseconds.", snapshot.maxLatencyMillis());
        appendGauge(builder, "intent_hub_latency_millis_p95", "Approximate p95 recognition latency in milliseconds.", snapshot.p95LatencyMillis());
        appendGauge(builder, "intent_hub_latency_millis_p99", "Approximate p99 recognition latency in milliseconds.", snapshot.p99LatencyMillis());
        snapshot.decisions().forEach((decision, count) ->
                appendGauge(builder, "intent_hub_decisions_total", "Decision counts.", count, "decision", decision));
        snapshot.intents().forEach((intent, count) ->
                appendGauge(builder, "intent_hub_intents_total", "Intent counts.", count, "intent", intent));
        snapshot.scenes().forEach((scene, count) ->
                appendGauge(builder, "intent_hub_scenes_total", "Scene counts.", count, "scene", scene));
        return builder.toString();
    }

    private void appendGauge(StringBuilder builder, String name, String help, long value) {
        appendGauge(builder, name, help, Long.toString(value));
    }

    private void appendGauge(StringBuilder builder, String name, String help, double value) {
        appendGauge(builder, name, help, Double.toString(value));
    }

    private void appendGauge(StringBuilder builder, String name, String help, long value, String labelName, String labelValue) {
        builder.append("# HELP ").append(name).append(' ').append(help).append('\n');
        builder.append("# TYPE ").append(name).append(" gauge\n");
        builder.append(name)
                .append('{')
                .append(labelName)
                .append("=\"")
                .append(escapeLabel(labelValue))
                .append("\"} ")
                .append(value)
                .append('\n');
    }

    private void appendGauge(StringBuilder builder, String name, String help, String value) {
        builder.append("# HELP ").append(name).append(' ').append(help).append('\n');
        builder.append("# TYPE ").append(name).append(" gauge\n");
        builder.append(name).append(' ').append(value).append('\n');
    }

    private String escapeLabel(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
