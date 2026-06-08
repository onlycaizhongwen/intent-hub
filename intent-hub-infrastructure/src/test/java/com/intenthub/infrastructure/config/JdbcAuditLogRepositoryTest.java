package com.intenthub.infrastructure.config;

import com.intenthub.application.config.AuditLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuditLogRepositoryTest {
    private JdbcAuditLogRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:audit_log;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcAuditLogRepository(jdbcTemplate);
        jdbcTemplate.execute("drop table if exists audit_log");
        jdbcTemplate.execute("""
                create table audit_log (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64),
                    actor varchar(128),
                    action varchar(128) not null,
                    target_type varchar(128) not null,
                    target_id varchar(128),
                    detail jsonb not null default '{}'::jsonb,
                    created_at timestamp not null default now()
                )
                """);
    }

    @Test
    void listsAuditLogsByConfigVersionDescending() {
        repository.record("demo", "order-scene", "admin", "CONFIG_DRAFT_CREATED", "CONFIG_VERSION", "v1", Map.of("status", "DRAFT"));
        repository.record("demo", "order-scene", "admin", "CONFIG_PUBLISHED", "CONFIG_VERSION", "v1", Map.of("publishedVersion", "v1"));
        repository.record("demo", "order-scene", "admin", "CONFIG_DRAFT_CREATED", "CONFIG_VERSION", "v2", Map.of("status", "DRAFT"));

        List<AuditLogEntry> entries = repository.list("demo", "order-scene", "CONFIG_VERSION", "v1", 10);

        assertThat(entries).extracting(AuditLogEntry::action)
                .containsExactly("CONFIG_PUBLISHED", "CONFIG_DRAFT_CREATED");
        assertThat(entries.get(0).detail()).containsEntry("publishedVersion", "v1");
        assertThat(entries.get(0).createdAt()).isNotNull();
    }
}
