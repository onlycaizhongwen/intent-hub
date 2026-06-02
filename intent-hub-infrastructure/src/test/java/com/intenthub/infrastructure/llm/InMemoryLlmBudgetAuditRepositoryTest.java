package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetUsage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLlmBudgetAuditRepositoryTest {
    @Test
    void recordsDailyUsageByTenantAndScene() {
        InMemoryLlmBudgetAuditRepository repository = new InMemoryLlmBudgetAuditRepository();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        repository.recordAttempt("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0);
        repository.recordAttempt("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 2.0);
        repository.recordAttempt("tenant-a", "scene-b", "spring-ai-alibaba", "qwen-plus", 5.0);

        LlmBudgetUsage usage = repository.dailyUsage("tenant-a", "scene-a", today);

        assertThat(usage.tenantId()).isEqualTo("tenant-a");
        assertThat(usage.sceneId()).isEqualTo("scene-a");
        assertThat(usage.usageDate()).isEqualTo(today);
        assertThat(usage.attempts()).isEqualTo(2);
        assertThat(usage.consumedUnits()).isEqualTo(3.0);
    }
}
