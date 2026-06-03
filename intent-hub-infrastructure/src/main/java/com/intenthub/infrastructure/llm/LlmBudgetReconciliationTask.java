package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetAuditPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "intent-hub.llm.budget-reconciliation.enabled", havingValue = "true")
public class LlmBudgetReconciliationTask {
    private final LlmBudgetAuditPort budgetAuditPort;
    private final LlmBudgetReconciliationProperties properties;

    public LlmBudgetReconciliationTask(
            LlmBudgetAuditPort budgetAuditPort,
            LlmBudgetReconciliationProperties properties
    ) {
        this.budgetAuditPort = budgetAuditPort;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${intent-hub.llm.budget-reconciliation.interval:PT1M}")
    public void reconcile() {
        budgetAuditPort.reconcileStaleDailyBudgetReservations(properties.staleAfter());
    }
}
