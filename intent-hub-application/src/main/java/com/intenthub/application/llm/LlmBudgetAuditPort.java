package com.intenthub.application.llm;

import java.time.LocalDate;

public interface LlmBudgetAuditPort {
    void recordAttempt(String tenantId, String sceneId, String provider, String model, double units);

    LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate);
}
