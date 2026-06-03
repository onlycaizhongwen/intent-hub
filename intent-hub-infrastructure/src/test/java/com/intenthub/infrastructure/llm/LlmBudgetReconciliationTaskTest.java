package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmBudgetReconciliationTaskTest {
    @Test
    void delegatesReconciliationWithConfiguredStaleWindow() {
        RecordingBudgetAuditPort budgetAuditPort = new RecordingBudgetAuditPort();
        LlmBudgetReconciliationProperties properties = new LlmBudgetReconciliationProperties(
                true,
                Duration.ofMinutes(7),
                Duration.ofMinutes(2)
        );
        LlmBudgetReconciliationTask task = new LlmBudgetReconciliationTask(budgetAuditPort, properties);

        task.reconcile();

        assertThat(budgetAuditPort.calls.get()).isEqualTo(1);
        assertThat(budgetAuditPort.staleAfter.get()).isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    void normalizesInvalidDurations() {
        LlmBudgetReconciliationProperties properties = new LlmBudgetReconciliationProperties(
                true,
                Duration.ofSeconds(-1),
                Duration.ZERO
        );

        assertThat(properties.staleAfter()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.interval()).isEqualTo(Duration.ofMinutes(1));
    }

    private static final class RecordingBudgetAuditPort implements LlmBudgetAuditPort {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<Duration> staleAfter = new AtomicReference<>();

        @Override
        public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
        }

        @Override
        public boolean tryReserveDailyBudget(String tenantId, String sceneId, String provider, String model, double units, double dailyBudget) {
            return false;
        }

        @Override
        public void releaseDailyBudgetReservation(String tenantId, String sceneId, String provider, String model, double units) {
        }

        @Override
        public int reconcileStaleDailyBudgetReservations(Duration staleAfter) {
            this.calls.incrementAndGet();
            this.staleAfter.set(staleAfter);
            return 1;
        }

        @Override
        public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
            return new LlmBudgetUsage(tenantId, sceneId, usageDate, 0, 0.0);
        }
    }
}
