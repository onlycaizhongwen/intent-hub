package com.intenthub.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "intent-hub.llm.budget-reconciliation")
public record LlmBudgetReconciliationProperties(
        boolean enabled,
        Duration staleAfter,
        Duration interval
) {
    public LlmBudgetReconciliationProperties {
        if (staleAfter == null || staleAfter.isNegative()) {
            staleAfter = Duration.ofMinutes(5);
        }
        if (interval == null || interval.isNegative() || interval.isZero()) {
            interval = Duration.ofMinutes(1);
        }
    }
}
