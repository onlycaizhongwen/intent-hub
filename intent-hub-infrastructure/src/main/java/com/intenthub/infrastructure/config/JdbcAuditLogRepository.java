package com.intenthub.infrastructure.config;

import com.intenthub.application.config.AuditLogEntry;
import com.intenthub.application.config.AuditLogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcAuditLogRepository implements AuditLogPort {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Override
    public List<AuditLogEntry> list(String tenantId, String sceneId, String targetType, String targetId, int limit) {
        return jdbcTemplate.query("""
                        select id, tenant_id, scene_id, actor, action, target_type, target_id, detail::text as detail, created_at
                        from audit_log
                        where tenant_id = ?
                          and scene_id = ?
                          and target_type = ?
                          and target_id = ?
                        order by id desc
                        limit ?
                        """,
                (rs, rowNum) -> new AuditLogEntry(
                        rs.getLong("id"),
                        rs.getString("tenant_id"),
                        rs.getString("scene_id"),
                        rs.getString("actor"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        detailMap(rs.getString("detail")),
                        instant(rs.getTimestamp("created_at"))
                ),
                tenantId,
                sceneId,
                targetType,
                targetId,
                Math.max(1, limit)
        );
    }

    private Map<String, String> detailMap(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, String> values = new LinkedHashMap<>();
            node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText()));
            return values;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
