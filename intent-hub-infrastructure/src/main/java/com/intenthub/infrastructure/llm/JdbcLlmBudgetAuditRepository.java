package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcLlmBudgetAuditRepository implements LlmBudgetAuditPort {
    private static final String BUDGET_PROVIDER = "__budget__";
    private static final String BUDGET_MODEL = "__daily__";

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
        if (updateAttempt(normalizedTenant, normalizedScene, usageDate, normalizedProvider, normalizedModel, boundedUnits)) {
            return;
        }
        try {
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
        } catch (DuplicateKeyException ex) {
            updateAttempt(normalizedTenant, normalizedScene, usageDate, normalizedProvider, normalizedModel, boundedUnits);
        }
    }

    @Override
    public boolean tryReserveDailyBudget(String tenantId, String sceneId, String provider, String model, double units, double dailyBudget) {
        double boundedUnits = Math.max(0.0, units);
        double boundedBudget = Math.max(0.0, dailyBudget);
        if (boundedUnits == 0.0 || boundedBudget == 0.0 || boundedUnits > boundedBudget) {
            return false;
        }
        String normalizedTenant = normalize(tenantId);
        String normalizedScene = normalize(sceneId);
        LocalDate usageDate = LocalDate.now(ZoneOffset.UTC);
        if (reserveExisting(normalizedTenant, normalizedScene, usageDate, boundedUnits, boundedBudget)) {
            return true;
        }
        if (budgetRowExists(normalizedTenant, normalizedScene, usageDate)) {
            return false;
        }
        try {
            jdbcTemplate.update("""
                            insert into llm_budget_usage (
                                tenant_id, scene_id, usage_date, provider, model,
                                attempt_count, consumed_units, created_at, updated_at
                            ) values (?, ?, ?, ?, ?, 1, ?, now(), now())
                            """,
                    normalizedTenant,
                    normalizedScene,
                    Date.valueOf(usageDate),
                    BUDGET_PROVIDER,
                    BUDGET_MODEL,
                    boundedUnits
            );
            return true;
        } catch (DuplicateKeyException ex) {
            return reserveExisting(normalizedTenant, normalizedScene, usageDate, boundedUnits, boundedBudget);
        }
    }

    @Override
    public void releaseDailyBudgetReservation(String tenantId, String sceneId, String provider, String model, double units) {
        double boundedUnits = Math.max(0.0, units);
        if (boundedUnits == 0.0) {
            return;
        }
        jdbcTemplate.update("""
                        update llm_budget_usage
                           set attempt_count = case when attempt_count > 0 then attempt_count - 1 else 0 end,
                               consumed_units = greatest(consumed_units - ?, 0),
                               updated_at = now()
                         where tenant_id = ?
                           and scene_id = ?
                           and usage_date = ?
                           and provider = ?
                           and model = ?
                        """,
                boundedUnits,
                normalize(tenantId),
                normalize(sceneId),
                Date.valueOf(LocalDate.now(ZoneOffset.UTC)),
                BUDGET_PROVIDER,
                BUDGET_MODEL
        );
    }

    @Override
    public int reconcileStaleDailyBudgetReservations(Duration staleAfter) {
        Duration effectiveStaleAfter = staleAfter == null || staleAfter.isNegative() ? Duration.ZERO : staleAfter;
        Timestamp cutoff = Timestamp.from(Instant.now().minus(effectiveStaleAfter));
        List<BudgetRow> staleBudgetRows = jdbcTemplate.query("""
                        select tenant_id, scene_id, usage_date, attempt_count, consumed_units
                          from llm_budget_usage
                         where provider = ?
                           and model = ?
                           and updated_at <= ?
                        """,
                (rs, rowNum) -> new BudgetRow(
                        rs.getString("tenant_id"),
                        rs.getString("scene_id"),
                        rs.getDate("usage_date").toLocalDate(),
                        rs.getLong("attempt_count"),
                        rs.getDouble("consumed_units")
                ),
                BUDGET_PROVIDER,
                BUDGET_MODEL,
                cutoff
        );

        int reconciled = 0;
        for (BudgetRow row : staleBudgetRows) {
            LlmBudgetUsage usage = dailyUsage(row.tenantId(), row.sceneId(), row.usageDate());
            if (usage.pendingUnits() <= 0.0) {
                continue;
            }
            long targetAttempts = Math.min(row.attempts(), usage.attempts());
            int updated = jdbcTemplate.update("""
                            update llm_budget_usage
                               set attempt_count = ?,
                                   consumed_units = ?,
                                   updated_at = now()
                             where tenant_id = ?
                               and scene_id = ?
                               and usage_date = ?
                               and provider = ?
                               and model = ?
                               and updated_at <= ?
                            """,
                    targetAttempts,
                    usage.consumedUnits(),
                    row.tenantId(),
                    row.sceneId(),
                    Date.valueOf(row.usageDate()),
                    BUDGET_PROVIDER,
                    BUDGET_MODEL,
                    cutoff
            );
            reconciled += updated;
        }
        return reconciled;
    }

    @Override
    public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
        String normalizedTenant = normalize(tenantId);
        String normalizedScene = normalize(sceneId);
        LocalDate date = usageDate == null ? LocalDate.now(ZoneOffset.UTC) : usageDate;
        return jdbcTemplate.queryForObject("""
                        select coalesce(sum(case when provider <> ? then attempt_count else 0 end), 0) as attempts,
                               coalesce(sum(case when provider <> ? then consumed_units else 0 end), 0) as consumed_units,
                               coalesce(sum(case when provider = ? then attempt_count else 0 end), 0) as reserved_attempts,
                               coalesce(sum(case when provider = ? then consumed_units else 0 end), 0) as reserved_units
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
                        rs.getDouble("consumed_units"),
                        rs.getLong("reserved_attempts"),
                        rs.getDouble("reserved_units")
                ),
                BUDGET_PROVIDER,
                BUDGET_PROVIDER,
                BUDGET_PROVIDER,
                BUDGET_PROVIDER,
                normalizedTenant,
                normalizedScene,
                Date.valueOf(date)
        );
    }

    private boolean reserveExisting(String tenantId, String sceneId, LocalDate usageDate, double units, double dailyBudget) {
        return jdbcTemplate.update("""
                        update llm_budget_usage
                           set attempt_count = attempt_count + 1,
                               consumed_units = consumed_units + ?,
                               updated_at = now()
                         where tenant_id = ?
                           and scene_id = ?
                           and usage_date = ?
                           and provider = ?
                           and model = ?
                           and consumed_units + ? <= ?
                        """,
                units,
                tenantId,
                sceneId,
                Date.valueOf(usageDate),
                BUDGET_PROVIDER,
                BUDGET_MODEL,
                units,
                dailyBudget
        ) == 1;
    }

    private boolean budgetRowExists(String tenantId, String sceneId, LocalDate usageDate) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                          from llm_budget_usage
                         where tenant_id = ?
                           and scene_id = ?
                           and usage_date = ?
                           and provider = ?
                           and model = ?
                        """,
                Integer.class,
                tenantId,
                sceneId,
                Date.valueOf(usageDate),
                BUDGET_PROVIDER,
                BUDGET_MODEL
        );
        return count != null && count > 0;
    }

    private boolean updateAttempt(String tenantId, String sceneId, LocalDate usageDate, String provider, String model, double units) {
        return jdbcTemplate.update("""
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
                units,
                tenantId,
                sceneId,
                Date.valueOf(usageDate),
                provider,
                model
        ) == 1;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private record BudgetRow(String tenantId, String sceneId, LocalDate usageDate, long attempts, double consumedUnits) {
    }
}
