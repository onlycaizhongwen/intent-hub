package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
        assertThat(usage.reservedAttempts()).isZero();
        assertThat(usage.reservedUnits()).isZero();
        assertThat(usage.pendingUnits()).isZero();
    }

    @Test
    void reservesBudgetWithoutDoubleCountingDailyUsage() {
        InMemoryLlmBudgetAuditRepository repository = new InMemoryLlmBudgetAuditRepository();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(repository.tryReserveDailyBudget("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0, 1.0))
                .isTrue();
        assertThat(repository.tryReserveDailyBudget("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0, 1.0))
                .isFalse();
        repository.recordAttempt("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0);

        LlmBudgetUsage usage = repository.dailyUsage("tenant-a", "scene-a", today);

        assertThat(usage.attempts()).isEqualTo(1);
        assertThat(usage.consumedUnits()).isEqualTo(1.0);
        assertThat(usage.reservedAttempts()).isEqualTo(1);
        assertThat(usage.reservedUnits()).isEqualTo(1.0);
        assertThat(usage.pendingUnits()).isZero();
    }

    @Test
    void exposesPendingReservedUsageWhenAttemptAuditIsMissing() {
        InMemoryLlmBudgetAuditRepository repository = new InMemoryLlmBudgetAuditRepository();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(repository.tryReserveDailyBudget("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0, 2.0))
                .isTrue();

        LlmBudgetUsage usage = repository.dailyUsage("tenant-a", "scene-a", today);

        assertThat(usage.attempts()).isZero();
        assertThat(usage.consumedUnits()).isZero();
        assertThat(usage.reservedAttempts()).isEqualTo(1);
        assertThat(usage.reservedUnits()).isEqualTo(1.0);
        assertThat(usage.pendingUnits()).isEqualTo(1.0);
    }

    @Test
    void releasesReservedUsageAfterFailedProviderAttempt() {
        InMemoryLlmBudgetAuditRepository repository = new InMemoryLlmBudgetAuditRepository();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(repository.tryReserveDailyBudget("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0, 1.0))
                .isTrue();
        repository.releaseDailyBudgetReservation("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0);

        LlmBudgetUsage usage = repository.dailyUsage("tenant-a", "scene-a", today);

        assertThat(usage.reservedAttempts()).isZero();
        assertThat(usage.reservedUnits()).isZero();
        assertThat(usage.pendingUnits()).isZero();
        assertThat(repository.tryReserveDailyBudget("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0, 1.0))
                .isTrue();
    }

    @Test
    void reconcilesPendingReservedUsageToConfirmedUsage() {
        InMemoryLlmBudgetAuditRepository repository = new InMemoryLlmBudgetAuditRepository();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(repository.tryReserveDailyBudget("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0, 2.0))
                .isTrue();

        assertThat(repository.reconcileStaleDailyBudgetReservations(Duration.ZERO)).isEqualTo(1);

        LlmBudgetUsage usage = repository.dailyUsage("tenant-a", "scene-a", today);

        assertThat(usage.reservedAttempts()).isZero();
        assertThat(usage.reservedUnits()).isZero();
        assertThat(usage.pendingUnits()).isZero();
    }
}
