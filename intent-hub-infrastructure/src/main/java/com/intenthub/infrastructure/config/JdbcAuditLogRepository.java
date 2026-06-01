package com.intenthub.infrastructure.config;

import com.intenthub.application.config.AuditLogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcAuditLogRepository implements AuditLogPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String tenantId, String sceneId, String actor, String action, String targetType, String targetId, Map<String, String> detail) {
        jdbcTemplate.update("""
                        insert into audit_log (tenant_id, scene_id, actor, action, target_type, target_id, detail)
                        values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                        """,
                tenantId,
                sceneId,
                actor,
                action,
                targetType,
                targetId,
                ConfigJsonSupport.map(detail == null ? Map.of() : detail)
        );
    }
}
