package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcLlmBudgetAuditRepositoryTest {
    private JdbcTemplate jdbcTemplate;
    private JdbcLlmBudgetAuditRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:llm_budget;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcLlmBudgetAuditRepository(jdbcTemplate);
        resetSchema();
    }

    @Test
    void upsertsAndAggregatesDailyUsageAcrossProviders() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        repository.recordAttempt("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 1.0);
        repository.recordAttempt("tenant-a", "scene-a", "spring-ai-alibaba", "qwen-plus", 2.0);
        repository.recordAttempt("tenant-a", "scene-a", "http-contract", "mock", 0.5);
        repository.recordAttempt("tenant-a", "scene-b", "spring-ai-alibaba", "qwen-plus", 9.0);

        LlmBudgetUsage usage = repository.dailyUsage("tenant-a", "scene-a", today);

        assertThat(usage.attempts()).isEqualTo(3);
        assertThat(usage.consumedUnits()).isEqualTo(3.5);
        assertThat(jdbcTemplate.queryForObject("select count(*) from llm_budget_usage where tenant_id = ? and scene_id = ?", Long.class, "tenant-a", "scene-a"))
                .isEqualTo(2L);
    }

    private void resetSchema() {
        jdbcTemplate.execute("drop table if exists llm_budget_usage");
        jdbcTemplate.execute("""
                create table llm_budget_usage (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    usage_date date not null,
                    provider varchar(128) not null,
                    model varchar(128) not null,
                    attempt_count bigint not null default 0,
                    consumed_units numeric(12, 4) not null default 0,
                    created_at timestamp not null default now(),
                    updated_at timestamp not null default now(),
                    unique (tenant_id, scene_id, usage_date, provider, model)
                )
                """);
    }
}
