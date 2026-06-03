package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.metrics.IntentMetricsPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "intent-hub.llm.budget-reconciliation.enabled", havingValue = "true")
public class LlmBudgetReconciliationTask {
    private final LlmBudgetAuditPort budgetAuditPort;
    private final IntentMetricsPort metricsPort;
    private final LlmBudgetReconciliationProperties properties;

    public LlmBudgetReconciliationTask(
            LlmBudgetAuditPort budgetAuditPort,
            IntentMetricsPort metricsPort,
            LlmBudgetReconciliationProperties properties
    ) {
        this.budgetAuditPort = budgetAuditPort;
        this.metricsPort = metricsPort;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${intent-hub.llm.budget-reconciliation.interval:PT1M}")
    public void reconcile() {
        int reconciled = budgetAuditPort.reconcileStaleDailyBudgetReservations(properties.staleAfter());
        metricsPort.recordLlmBudgetReconciliation(reconciled);
    }
}
