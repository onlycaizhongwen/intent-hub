package com.intenthub.infrastructure.config;

import com.intenthub.application.config.AuditLogEntry;
import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuditLogRepositoryTest {
    private JdbcAuditLogRepository repository;
    private JdbcTemplate jdbcTemplate;
    private RecordingMetricsPort metricsPort;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:audit_log;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        metricsPort = new RecordingMetricsPort();
        repository = new JdbcAuditLogRepository(jdbcTemplate, metricsPort);
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
        assertThat(metricsPort.permissionDenied.get()).isZero();
    }

    @Test
    void recordsPermissionDeniedMetricsWhenAuditEventIsPermissionDenied() {
        repository.record("demo", "order-scene", "unknown", "CONFIG_PERMISSION_DENIED", "CONFIG_PERMISSION", "order-scene", Map.of(
                "action", "approve config version",
                "requiredRole", "CONFIG_APPROVER"
        ));

        assertThat(metricsPort.permissionDenied.get()).isEqualTo(1);
    }

    private static final class RecordingMetricsPort implements IntentMetricsPort {
        private final AtomicLong permissionDenied = new AtomicLong();

        @Override
        public void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis) {
        }

        @Override
        public void recordPermissionDenied(String tenantId, String sceneId, String action) {
            permissionDenied.incrementAndGet();
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(0, 0, 0, 0, 0, 0.0, 0, permissionDenied.get(), 0, 0.0, 0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        }
    }
}
