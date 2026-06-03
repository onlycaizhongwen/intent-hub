package com.intenthub.application.llm;

import java.time.LocalDate;
import java.time.ZoneOffset;

public class LlmBudgetAppService {
    private final LlmBudgetAuditPort budgetAuditPort;

    public LlmBudgetAppService(LlmBudgetAuditPort budgetAuditPort) {
        this.budgetAuditPort = budgetAuditPort;
    }

    public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
        LocalDate date = usageDate == null ? LocalDate.now(ZoneOffset.UTC) : usageDate;
        return budgetAuditPort.dailyUsage(tenantId, sceneId, date);
    }
}
