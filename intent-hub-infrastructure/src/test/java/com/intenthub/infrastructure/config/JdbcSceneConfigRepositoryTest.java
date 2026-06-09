package com.intenthub.infrastructure.config;

import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.InputType;
import com.intenthub.domain.recognition.RecognitionCandidate;
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

    @Test
    void loadsLlmPolicyFromPublishedStrategy() {
        publishScene("demo", "order-scene", "v-order", "ORDER_QUERY", "order");
        jdbcTemplate.update("""
                        insert into nlu_strategy (tenant_id, scene_id, version, strategy_code, confidence_threshold, llm_policy, model_policy)
                        values (?, ?, ?, 'default', 0.60, ?, ?)
                        """,
                "demo",
                "order-scene",
                "v-order",
                ConfigJsonSupport.objectMap(Map.of(
                        "enabled", true,
                        "provider", "spring-ai-alibaba",
                        "model", "qwen-plus",
                        "timeoutMs", 2500,
                        "maxRetries", 1,
                        "dailyBudget", 10.0,
                        "fallbackDecision", "REJECTED"
                )),
                ConfigJsonSupport.objectMap(Map.of(
                        "enabled", true,
                        "endpoint", "https://model.example.test",
                        "timeoutMs", 1800,
                        "minConfidence", 0.72,
                        "authTokenRef", "INTENT_HUB_MODEL_TOKEN"
                ))
        );

        SceneConfig config = repository.loadPublishedConfig(envelope(Map.of("scene_id", "order-scene")));

        assertThat(config.llmPolicy().enabled()).isTrue();
        assertThat(config.llmPolicy().provider()).isEqualTo("spring-ai-alibaba");
        assertThat(config.llmPolicy().model()).isEqualTo("qwen-plus");
        assertThat(config.llmPolicy().timeoutMs()).isEqualTo(2500);
        assertThat(config.llmPolicy().maxRetries()).isEqualTo(1);
        assertThat(config.llmPolicy().dailyBudget()).isEqualTo(10.0);
        assertThat(config.modelPolicy().enabled()).isTrue();
        assertThat(config.modelPolicy().endpoint()).isEqualTo("https://model.example.test");
        assertThat(config.modelPolicy().timeoutMs()).isEqualTo(1800);
        assertThat(config.modelPolicy().minConfidence()).isEqualTo(0.72);
        assertThat(config.modelPolicy().authTokenRef()).isEqualTo("INTENT_HUB_MODEL_TOKEN");
    }

    @Test
    void loadsPostRouteRulesFromPublishedSceneRoutingRules() {
        publishScene("demo", "order-scene", "v-order", "ORDER_CANCEL", "cancel");
        jdbcTemplate.update("""
                        insert into downstream_action (tenant_id, scene_id, version, action_code, action_type, target, idempotency_required, timeout_ms)
                        values (?, ?, ?, 'VIP_CANCEL_COMMAND', 'MQ', 'order.command.cancel.vip', true, 3000)
                        """,
                "demo",
                "order-scene",
                "v-order"
        );
        jdbcTemplate.update("""
                        insert into scene_routing_rule (tenant_id, scene_id, version, route_stage, priority, match_condition, route_target)
                        values (?, ?, ?, 'POST', 0, ?, 'VIP_CANCEL_COMMAND')
                        """,
                "demo",
                "order-scene",
                "v-order",
                ConfigJsonSupport.objectMap(Map.of(
                        "intentCode", "ORDER_CANCEL",
                        "minConfidence", 0.90,
                        "slots", Map.of("order_id", "VIP100")
                ))
        );

        SceneConfig config = repository.loadPublishedConfig(envelope(Map.of("scene_id", "order-scene")));

        assertThat(config.actionFor(new RecognitionCandidate("ORDER_CANCEL", 0.95, Map.of("order_id", "VIP100"), "rule hit")).actionCode())
                .isEqualTo("VIP_CANCEL_COMMAND");
        assertThat(config.actionFor(new RecognitionCandidate("ORDER_CANCEL", 0.95, Map.of("order_id", "A100"), "rule hit")).actionCode())
                .isEqualTo("ORDER_CANCEL_API");
    }

    @Test
    void mapsDownstreamActionToExplicitIntentCodeFromActionSchema() {
        publishScene("demo", "order-scene", "v-order", "ORDER_CANCEL", "cancel");
        jdbcTemplate.update("""
                        delete from downstream_action
                        where tenant_id = ? and scene_id = ? and version = ? and action_code = 'ORDER_CANCEL_API'
                        """,
                "demo",
                "order-scene",
                "v-order"
        );
        jdbcTemplate.update("""
                        insert into downstream_action (tenant_id, scene_id, version, action_code, action_type, target, idempotency_required, timeout_ms, action_schema)
                        values (?, ?, ?, 'VIP_CANCEL_COMMAND', 'MQ', 'order.command.cancel.vip', true, 3000, ?)
                        """,
                "demo",
                "order-scene",
                "v-order",
                ConfigJsonSupport.objectMap(Map.of("intentCode", "ORDER_CANCEL"))
        );

        SceneConfig config = repository.loadPublishedConfig(envelope(Map.of("scene_id", "order-scene")));

        assertThat(config.actionFor("ORDER_CANCEL").actionCode()).isEqualTo("VIP_CANCEL_COMMAND");
        assertThat(config.actionFor("VIP_CANCEL_COMMAND").actionCode()).isEqualTo("VIP_CANCEL_COMMAND");
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
        jdbcTemplate.execute("drop table if exists scene_routing_rule");
        jdbcTemplate.execute("drop table if exists nlu_strategy");
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
                    approved_snapshot_hash varchar(128),
                    approved_by varchar(128),
                    approved_at timestamp,
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
                create table nlu_strategy (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    version varchar(64) not null,
                    strategy_code varchar(128) not null,
                    confidence_threshold numeric(5,4) not null default 0.6000,
                    llm_policy varchar(2048) not null default '{}',
                    model_policy varchar(2048) not null default '{}'
                )
                """);
        jdbcTemplate.execute("""
                create table scene_routing_rule (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    version varchar(64) not null,
                    route_stage varchar(32) not null,
                    priority integer not null default 0,
                    match_condition varchar(2048) not null default '{}',
                    route_target varchar(256) not null
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
                    timeout_ms integer not null default 3000,
                    action_schema varchar(2048) not null default '{}'
                )
                """);
    }
}
