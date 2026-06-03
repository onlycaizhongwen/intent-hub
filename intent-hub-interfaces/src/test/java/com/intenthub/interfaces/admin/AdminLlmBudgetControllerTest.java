package com.intenthub.interfaces.admin;

import com.intenthub.application.llm.LlmBudgetAppService;
import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLlmBudgetControllerTest {
    @Test
    void exposesDailyBudgetUsageThroughControllerContract() {
        AdminLlmBudgetController controller = new AdminLlmBudgetController(
                new LlmBudgetAppService(new FixedBudgetAuditPort())
        );

        LlmBudgetUsage usage = controller.dailyUsage(
                "tenant-a",
                "order-scene",
                LocalDate.parse("2026-06-03")
        );

        assertThat(usage.tenantId()).isEqualTo("tenant-a");
        assertThat(usage.sceneId()).isEqualTo("order-scene");
        assertThat(usage.usageDate()).isEqualTo(LocalDate.parse("2026-06-03"));
        assertThat(usage.attempts()).isEqualTo(3);
        assertThat(usage.consumedUnits()).isEqualTo(3.0);
        assertThat(usage.reservedAttempts()).isEqualTo(4);
        assertThat(usage.reservedUnits()).isEqualTo(5.0);
        assertThat(usage.pendingUnits()).isEqualTo(2.0);
    }

    private static final class FixedBudgetAuditPort implements LlmBudgetAuditPort {
        @Override
        public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
        }

        @Override
        public boolean tryReserveDailyBudget(String tenantId, String sceneId, String provider, String model, double units, double dailyBudget) {
            return false;
        }

        @Override
        public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
            return new LlmBudgetUsage(tenantId, sceneId, usageDate, 3, 3.0, 4, 5.0);
        }
    }
}
