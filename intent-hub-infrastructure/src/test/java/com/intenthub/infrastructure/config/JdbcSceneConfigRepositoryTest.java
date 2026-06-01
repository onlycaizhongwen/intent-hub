package com.intenthub.infrastructure.config;

import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.InputType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSceneConfigRepositoryTest {
    private JdbcTemplate jdbcTemplate;
    private JdbcSceneConfigRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:scene_config;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcSceneConfigRepository(jdbcTemplate, JsonMapper.builder().build());
        resetSchema();
    }

    @Test
    void loadsPublishedConfigFromRequestedMetadataScene() {
        publishScene("demo", "order-scene", "v-order", "ORDER_QUERY", "order");
        publishScene("demo", "invoice-scene", "v-invoice", "INVOICE_QUERY", "invoice");

        SceneConfig config = repository.loadPublishedConfig(envelope(Map.of("scene_id", "invoice-scene")));

        assertThat(config.sceneId()).isEqualTo("invoice-scene");
        assertThat(config.version()).isEqualTo("v-invoice");
        assertThat(config.rules()).extracting("intentCode").containsExactly("INVOICE_QUERY");
        assertThat(config.actionFor("INVOICE_QUERY").actionCode()).isEqualTo("INVOICE_QUERY_API");
    }

    @Test
    void loadsLatestPublishedSceneWhenMetadataSceneIsAbsent() {
        publishScene("demo", "order-scene", "v-order", "ORDER_QUERY", "order");
        publishScene("demo", "invoice-scene", "v-invoice", "INVOICE_QUERY", "invoice");

        SceneConfig config = repository.loadPublishedConfig(envelope(Map.of()));

        assertThat(config.sceneId()).isEqualTo("invoice-scene");
        assertThat(config.version()).isEqualTo("v-invoice");
    }

    @Test
    void fallsBackToBuiltinConfigWhenRequestedSceneHasNoPublishedVersion() {
        publishScene("demo", "invoice-scene", "v-invoice", "INVOICE_QUERY", "invoice");

        SceneConfig config = repository.loadPublishedConfig(envelope(Map.of("sceneId", "missing-scene")));

        assertThat(config.sceneId()).isEqualTo("order-scene");
        assertThat(config.version()).isEqualTo("v1-p1");
    }

    private Envelope envelope(Map<String, String> metadata) {
        return new Envelope(
                "demo",
                "app",
                "chat",
                InputType.TEXT,
                "query",
                "REQ-SCENE",
                "TRACE-SCENE",
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                metadata,
                List.of()
        );
    }

    private void publishScene(String tenantId, String sceneId, String version, String intentCode, String pattern) {
        jdbcTemplate.update("""
                        insert into config_version (tenant_id, scene_id, version, status, published_at)
                        values (?, ?, ?, 'PUBLISHED', now())
                        """,
                tenantId,
                sceneId,
                version
        );
        jdbcTemplate.update("""
                        insert into intent_definition (tenant_id, scene_id, version, intent_code, intent_name, enabled, definition)
                        values (?, ?, ?, ?, ?, true, ?)
                        """,
                tenantId,
                sceneId,
                version,
                intentCode,
                intentCode,
                ConfigJsonSupport.objectMap(Map.of(
                        "matchType", "CONTAINS",
                        "pattern", pattern,
                        "confidence", 0.91
                ))
        );
        jdbcTemplate.update("""
                        insert into downstream_action (tenant_id, scene_id, version, action_code, action_type, target, idempotency_required, timeout_ms)
                        values (?, ?, ?, ?, 'HTTP', ?, false, 3000)
                        """,
                tenantId,
                sceneId,
                version,
                intentCode + "_API",
                "https://example.test/" + intentCode
        );
    }

    private void resetSchema() {
        jdbcTemplate.execute("drop table if exists downstream_action");
        jdbcTemplate.execute("drop table if exists slot_definition");
        jdbcTemplate.execute("drop table if exists intent_definition");
        jdbcTemplate.execute("drop table if exists config_version");
        jdbcTemplate.execute("""
                create table config_version (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    version varchar(64) not null,
                    status varchar(32) not null,
                    published_at timestamp,
                    unique (tenant_id, scene_id, version)
                )
                """);
        jdbcTemplate.execute("""
                create table intent_definition (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    version varchar(64) not null,
                    intent_code varchar(128) not null,
                    intent_name varchar(256) not null,
                    enabled boolean not null default true,
                    definition varchar(2048) not null default '{}'
                )
                """);
        jdbcTemplate.execute("""
                create table slot_definition (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    version varchar(64) not null,
                    intent_code varchar(128) not null,
                    slot_code varchar(128) not null,
                    required boolean not null default false
                )
                """);
        jdbcTemplate.execute("""
                create table downstream_action (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    version varchar(64) not null,
                    action_code varchar(128) not null,
                    action_type varchar(64) not null,
                    target varchar(512) not null,
                    idempotency_required boolean not null default false,
                    timeout_ms integer not null default 3000
                )
                """);
    }
}
