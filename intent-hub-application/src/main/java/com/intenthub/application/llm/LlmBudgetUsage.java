package com.intenthub.application.llm;

import java.time.LocalDate;

public record LlmBudgetUsage(
        String tenantId,
        String sceneId,
        LocalDate usageDate,
        long attempts,
        double consumedUnits,
        long reservedAttempts,
        double reservedUnits
) {
    public LlmBudgetUsage(String tenantId, String sceneId, LocalDate usageDate, long attempts, double consumedUnits) {
        this(tenantId, sceneId, usageDate, attempts, consumedUnits, attempts, consumedUnits);
    }

    public double pendingUnits() {
        return Math.max(0.0, reservedUnits - consumedUnits);
    }
}
