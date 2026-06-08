package com.intenthub.infrastructure.config;

import com.intenthub.application.config.ConfigObjectPort;
import com.intenthub.application.config.ConfigObjectType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcConfigObjectRepository implements ConfigObjectPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcConfigObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> upsert(String tenantId, String sceneId, String version, ConfigObjectType type, Map<String, Object> payload) {
        switch (type) {
            case INTENT -> upsertIntent(tenantId, sceneId, version, payload);
            case SLOT -> upsertSlot(tenantId, sceneId, version, payload);
            case SYNONYM -> upsertSynonym(tenantId, sceneId, version, payload);
            case STRATEGY -> upsertStrategy(tenantId, sceneId, version, payload);
            case ROUTE -> upsertRoute(tenantId, sceneId, version, payload);
            case DOWNSTREAM_ACTION -> upsertDownstreamAction(tenantId, sceneId, version, payload);
        }
        return payload;
    }

    @Override
    public List<Map<String, Object>> list(String tenantId, String sceneId, String version, ConfigObjectType type) {
        return switch (type) {
            case INTENT -> rows("intent_definition", tenantId, sceneId, version);
            case SLOT -> rows("slot_definition", tenantId, sceneId, version);
            case SYNONYM -> rows("synonym_mapping", tenantId, sceneId, version);
            case STRATEGY -> rows("nlu_strategy", tenantId, sceneId, version);
            case ROUTE -> rows("scene_routing_rule", tenantId, sceneId, version);
            case DOWNSTREAM_ACTION -> rows("downstream_action", tenantId, sceneId, version);
        };
    }

    @Override
    public boolean delete(String tenantId, String sceneId, String version, ConfigObjectType type, String objectId) {
        int affected = switch (type) {
            case INTENT -> deleteBySingleKey("intent_definition", "intent_code", tenantId, sceneId, version, objectId);
            case SLOT -> deleteSlot(tenantId, sceneId, version, objectId);
            case SYNONYM -> deleteBySingleKey("synonym_mapping", "term", tenantId, sceneId, version, objectId);
            case STRATEGY -> deleteBySingleKey("nlu_strategy", "strategy_code", tenantId, sceneId, version, objectId);
            case ROUTE -> deleteRoute(tenantId, sceneId, version, objectId);
            case DOWNSTREAM_ACTION -> deleteBySingleKey("downstream_action", "action_code", tenantId, sceneId, version, objectId);
        };
        return affected > 0;
    }

    private void upsertIntent(String tenantId, String sceneId, String version, Map<String, Object> payload) {
        jdbcTemplate.update("""
                        insert into intent_definition (tenant_id, scene_id, version, intent_code, intent_name, enabled, definition)
                        values (?, ?, ?, ?, ?, ?, ?::jsonb)
                        on conflict (tenant_id, scene_id, version, intent_code)
                        do update set intent_name = excluded.intent_name, enabled = excluded.enabled, definition = excluded.definition
                        """,
                tenantId, sceneId, version,
                payload.get("intentCode"),
                payload.get("intentName"),
                payload.get("enabled"),
                ConfigJsonSupport.objectMap(map(payload.get("definition")))
        );
    }

    private void upsertSlot(String tenantId, String sceneId, String version, Map<String, Object> payload) {
        jdbcTemplate.update("""
                        insert into slot_definition (tenant_id, scene_id, version, intent_code, slot_code, required, definition)
                        values (?, ?, ?, ?, ?, ?, ?::jsonb)
                        on conflict (tenant_id, scene_id, version, intent_code, slot_code)
                        do update set required = excluded.required, definition = excluded.definition
                        """,
                tenantId, sceneId, version,
                payload.get("intentCode"),
                payload.get("slotCode"),
                payload.get("required"),
                ConfigJsonSupport.objectMap(map(payload.get("definition")))
        );
    }

    private void upsertSynonym(String tenantId, String sceneId, String version, Map<String, Object> payload) {
        jdbcTemplate.update("""
                        delete from synonym_mapping
                        where tenant_id = ? and scene_id = ? and version = ? and term = ?
                        """,
                tenantId, sceneId, version, payload.get("term")
        );
        jdbcTemplate.update("""
                        insert into synonym_mapping (tenant_id, scene_id, version, term, normalized_term)
                        values (?, ?, ?, ?, ?)
                        """,
                tenantId, sceneId, version,
                payload.get("term"),
                payload.get("normalizedTerm")
        );
    }

    private void upsertStrategy(String tenantId, String sceneId, String version, Map<String, Object> payload) {
        jdbcTemplate.update("""
                        insert into nlu_strategy (tenant_id, scene_id, version, strategy_code, confidence_threshold, llm_policy, model_policy)
                        values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                        on conflict (tenant_id, scene_id, version, strategy_code)
                        do update set confidence_threshold = excluded.confidence_threshold, llm_policy = excluded.llm_policy, model_policy = excluded.model_policy
                        """,
                tenantId, sceneId, version,
                payload.get("strategyCode"),
                payload.get("confidenceThreshold"),
                ConfigJsonSupport.objectMap(map(payload.get("llmPolicy"))),
                ConfigJsonSupport.objectMap(map(payload.get("modelPolicy")))
        );
    }

    private void upsertRoute(String tenantId, String sceneId, String version, Map<String, Object> payload) {
        jdbcTemplate.update("""
                        delete from scene_routing_rule
                        where tenant_id = ? and scene_id = ? and version = ? and route_stage = ? and route_target = ?
                        """,
                tenantId, sceneId, version,
                payload.get("routeStage"),
                payload.get("routeTarget")
        );
        jdbcTemplate.update("""
                        insert into scene_routing_rule (tenant_id, scene_id, version, route_stage, priority, match_condition, route_target)
                        values (?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                tenantId, sceneId, version,
                payload.get("routeStage"),
                payload.get("priority"),
                ConfigJsonSupport.objectMap(map(payload.get("matchCondition"))),
                payload.get("routeTarget")
        );
    }

    private void upsertDownstreamAction(String tenantId, String sceneId, String version, Map<String, Object> payload) {
        jdbcTemplate.update("""
                        insert into downstream_action (tenant_id, scene_id, version, action_code, action_type, target, idempotency_required, timeout_ms, action_schema)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        on conflict (tenant_id, scene_id, version, action_code)
                        do update set action_type = excluded.action_type, target = excluded.target, idempotency_required = excluded.idempotency_required, timeout_ms = excluded.timeout_ms, action_schema = excluded.action_schema
                        """,
                tenantId, sceneId, version,
                payload.get("actionCode"),
                payload.get("actionType"),
                payload.get("target"),
                payload.get("idempotencyRequired"),
                payload.get("timeoutMs"),
                ConfigJsonSupport.objectMap(map(payload.get("actionSchema")))
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
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

    private int deleteBySingleKey(String tableName, String columnName, String tenantId, String sceneId, String version, String objectId) {
        return jdbcTemplate.update("""
                        delete from %s
                        where tenant_id = ? and scene_id = ? and version = ? and %s = ?
                        """.formatted(tableName, columnName),
                tenantId,
                sceneId,
                version,
                objectId
        );
    }

    private int deleteSlot(String tenantId, String sceneId, String version, String objectId) {
        String[] parts = objectId.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("slot objectId must be intentCode.slotCode");
        }
        return jdbcTemplate.update("""
                        delete from slot_definition
                        where tenant_id = ? and scene_id = ? and version = ? and intent_code = ? and slot_code = ?
                        """,
                tenantId,
                sceneId,
                version,
                parts[0],
                parts[1]
        );
    }

    private int deleteRoute(String tenantId, String sceneId, String version, String objectId) {
        String[] parts = objectId.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("route objectId must be routeStage.routeTarget");
        }
        return jdbcTemplate.update("""
                        delete from scene_routing_rule
                        where tenant_id = ? and scene_id = ? and version = ? and route_stage = ? and route_target = ?
                        """,
                tenantId,
                sceneId,
                version,
                parts[0],
                parts[1]
        );
    }
}
