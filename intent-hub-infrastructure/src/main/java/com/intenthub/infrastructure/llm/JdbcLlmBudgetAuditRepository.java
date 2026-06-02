package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcLlmBudgetAuditRepository implements LlmBudgetAuditPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLlmBudgetAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
        double boundedUnits = Math.max(0.0, units);
        if (boundedUnits == 0.0) {
            return;
        }
        String normalizedTenant = normalize(tenantId);
        String normalizedScene = normalize(sceneId);
        String normalizedProvider = normalize(provider);
        String normalizedModel = normalize(model);
        LocalDate usageDate = LocalDate.now(ZoneOffset.UTC);
        int updated = jdbcTemplate.update("""
                        update llm_budget_usage
                           set attempt_count = attempt_count + 1,
                               consumed_units = consumed_units + ?,
                               updated_at = now()
                         where tenant_id = ?
                           and scene_id = ?
                           and usage_date = ?
                           and provider = ?
                           and model = ?
                        """,
                boundedUnits,
                normalizedTenant,
                normalizedScene,
                Date.valueOf(usageDate),
                normalizedProvider,
                normalizedModel
        );
        if (updated == 0) {
            jdbcTemplate.update("""
                            insert into llm_budget_usage (
                                tenant_id, scene_id, usage_date, provider, model,
                                attempt_count, consumed_units, created_at, updated_at
                            ) values (?, ?, ?, ?, ?, 1, ?, now(), now())
                            """,
                    normalizedTenant,
                    normalizedScene,
                    Date.valueOf(usageDate),
                    normalizedProvider,
                    normalizedModel,
                    boundedUnits
            );
        }
    }

    @Override
    public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
        String normalizedTenant = normalize(tenantId);
        String normalizedScene = normalize(sceneId);
        LocalDate date = usageDate == null ? LocalDate.now(ZoneOffset.UTC) : usageDate;
        return jdbcTemplate.queryForObject("""
                        select coalesce(sum(attempt_count), 0) as attempts,
                               coalesce(sum(consumed_units), 0) as consumed_units
                          from llm_budget_usage
                         where tenant_id = ?
                           and scene_id = ?
                           and usage_date = ?
                        """,
                (rs, rowNum) -> new LlmBudgetUsage(
                        normalizedTenant,
                        normalizedScene,
                        date,
                        rs.getLong("attempts"),
                        rs.getDouble("consumed_units")
                ),
                normalizedTenant,
                normalizedScene,
                Date.valueOf(date)
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
