package com.intenthub.application.llm;

import java.time.LocalDate;

public record LlmBudgetUsage(
        String tenantId,
        String sceneId,
        LocalDate usageDate,
        long attempts,
        double consumedUnits
) {
}
