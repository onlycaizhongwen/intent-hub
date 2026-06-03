package com.intenthub.infrastructure.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "intent-hub.llm.budget-reconciliation.enabled", havingValue = "true")
public class LlmBudgetReconciliationSchedulingConfiguration {
}
