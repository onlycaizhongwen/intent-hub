package com.intenthub.infrastructure.config;

import com.intenthub.application.config.ConfigBundle;
import com.intenthub.application.config.ConfigVersionInfo;
import com.intenthub.application.config.ConfigVersionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcConfigVersionRepository implements ConfigVersionPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcConfigVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ConfigVersionInfo createDraft(String tenantId, String sceneId, String version, String description, String actor) {
        jdbcTemplate.update("""
                        insert into config_version (tenant_id, scene_id, version, status, description, created_by)
                        values (?, ?, ?, 'DRAFT', ?, ?)
                        on conflict (tenant_id, scene_id, version)
                        do update set description = excluded.description
                        """,
                tenantId,
                sceneId,
                version,
                description,
                actor
        );
        return find(tenantId, sceneId, version).orElseThrow();
    }

    @Override
    public Optional<ConfigVersionInfo> find(String tenantId, String sceneId, String version) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            select tenant_id, scene_id, version, status, description, created_by, created_at, published_at
                            from config_version
                            where tenant_id = ? and scene_id = ? and version = ?
                            """,
                    this::mapVersion,
                    tenantId,
                    sceneId,
                    version
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public ConfigBundle exportBundle(String tenantId, String sceneId, String version) {
        ConfigVersionInfo info = find(tenantId, sceneId, version)
                .orElseThrow(() -> new IllegalArgumentException("config version not found"));
        return new ConfigBundle(
                info,
                rows("intent_definition", tenantId, sceneId, version),
                rows("slot_definition", tenantId, sceneId, version),
                rows("synonym_mapping", tenantId, sceneId, version),
                rows("nlu_strategy", tenantId, sceneId, version),
                rows("scene_routing_rule", tenantId, sceneId, version),
                rows("downstream_action", tenantId, sceneId, version)
        );
    }

    @Override
    public void importBundle(String tenantId, String sceneId, String version, ConfigBundle bundle, String actor) {
        ConfigVersionInfo source = bundle == null ? null : bundle.version();
        createDraft(
                tenantId,
                sceneId,
                version,
                source == null ? "Imported config bundle" : source.description(),
                actor
        );
    }

    @Override
    public void publish(String tenantId, String sceneId, String version, String actor) {
        jdbcTemplate.update("""
                        update config_version
                        set status = 'ARCHIVED'
                        where tenant_id = ? and scene_id = ? and status = 'PUBLISHED' and version <> ?
                        """,
                tenantId,
                sceneId,
                version
        );
        jdbcTemplate.update("""
                        update config_version
                        set status = 'PUBLISHED', published_at = ?
                        where tenant_id = ? and scene_id = ? and version = ?
                        """,
                Timestamp.from(Instant.now()),
                tenantId,
                sceneId,
                version
        );
    }

    @Override
    public void rollback(String tenantId, String sceneId, String targetVersion, String actor) {
        publish(tenantId, sceneId, targetVersion, actor);
    }

    private ConfigVersionInfo mapVersion(ResultSet rs, int rowNum) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new ConfigVersionInfo(
                rs.getString("tenant_id"),
                rs.getString("scene_id"),
                rs.getString("version"),
                rs.getString("status"),
                rs.getString("description"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                publishedAt == null ? null : publishedAt.toInstant()
        );
    }

    private List<Map<String, Object>> rows(String tableName, String tenantId, String sceneId, String version) {
        return jdbcTemplate.queryForList("""
                        select *
                        from %s
                        where tenant_id = ? and scene_id = ? and version = ?
                        order by id
                        """.formatted(tableName),
                tenantId,
                sceneId,
                version
        );
    }
}
