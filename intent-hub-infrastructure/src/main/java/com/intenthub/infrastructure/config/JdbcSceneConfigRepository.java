package com.intenthub.infrastructure.config;

import com.intenthub.application.SceneConfigPort;
import com.intenthub.domain.config.IntentRule;
import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.config.ModelPolicy;
import com.intenthub.domain.config.PostRouteRule;
import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcSceneConfigRepository implements SceneConfigPort {
    private static final String DEFAULT_SCENE_ID = "order-scene";

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public JdbcSceneConfigRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public SceneConfig loadPublishedConfig(Envelope envelope) {
        return resolvePublishedScene(envelope)
                .map(publishedScene -> load(envelope, publishedScene))
                .orElseGet(() -> BuiltinSceneConfigFactory.orderScene(envelope));
    }

    private SceneConfig load(Envelope envelope, PublishedScene publishedScene) {
        List<IntentRule> rules = jdbcTemplate.query("""
                        select intent_code, definition
                        from intent_definition
                        where tenant_id = ? and scene_id = ? and version = ? and enabled = true
                        order by id
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> definition = json(rs.getString("definition"));
                    return new IntentRule(
                            rs.getString("intent_code"),
                            string(definition, "matchType", "CONTAINS"),
                            string(definition, "pattern", rs.getString("intent_code")),
                            decimal(definition, "confidence", "0.900").doubleValue(),
                            string(definition, "explanation", "命中已发布配置"),
                            stringMap(definition.get("fixedSlots"))
                    );
                },
                envelope.tenantId(),
                publishedScene.sceneId(),
                publishedScene.version()
        );

        Map<String, List<String>> requiredSlots = jdbcTemplate.queryForList("""
                        select intent_code, slot_code
                        from slot_definition
                        where tenant_id = ? and scene_id = ? and version = ? and required = true
                        order by id
                        """,
                envelope.tenantId(),
                publishedScene.sceneId(),
                publishedScene.version()
        ).stream().collect(Collectors.groupingBy(
                row -> row.get("intent_code").toString(),
                LinkedHashMap::new,
                Collectors.mapping(row -> row.get("slot_code").toString(), Collectors.toList())
        ));

        List<Map<String, Object>> actionRows = jdbcTemplate.queryForList("""
                        select action_code, action_type, target, idempotency_required, timeout_ms
                        from downstream_action
                        where tenant_id = ? and scene_id = ? and version = ?
                        order by id
                        """,
                envelope.tenantId(),
                publishedScene.sceneId(),
                publishedScene.version()
        );
        Map<String, DownstreamAction> actions = new LinkedHashMap<>();
        for (Map<String, Object> row : actionRows) {
            String actionCode = row.get("action_code").toString();
            DownstreamAction action = new DownstreamAction(
                    actionCode,
                    row.get("action_type").toString(),
                    row.get("target").toString(),
                    Boolean.parseBoolean(row.get("idempotency_required").toString()),
                    Integer.parseInt(row.get("timeout_ms").toString())
            );
            actions.putIfAbsent(inferIntentCode(actionCode), action);
            actions.putIfAbsent(actionCode, action);
        }
        Map<String, Object> strategy = loadStrategy(envelope, publishedScene);
        ModelPolicy modelPolicy = toModelPolicy(strategy);
        LlmPolicy llmPolicy = toLlmPolicy(strategy);
        List<PostRouteRule> postRouteRules = loadPostRouteRules(envelope, publishedScene);

        return new SceneConfig(
                envelope.tenantId(),
                publishedScene.sceneId(),
                publishedScene.version(),
                0.60,
                rules.isEmpty() ? BuiltinSceneConfigFactory.orderScene(envelope).rules() : rules,
                requiredSlots,
                actions,
                postRouteRules,
                modelPolicy,
                llmPolicy
        );
    }

    private List<PostRouteRule> loadPostRouteRules(Envelope envelope, PublishedScene publishedScene) {
        return jdbcTemplate.query("""
                        select priority, match_condition, route_target
                        from scene_routing_rule
                        where tenant_id = ? and scene_id = ? and version = ? and route_stage = 'POST'
                        order by priority asc, id asc
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> condition = json(rs.getString("match_condition"));
                    return new PostRouteRule(
                            rs.getInt("priority"),
                            rs.getString("route_target"),
                            string(condition, "intentCode", string(condition, "intent_code", "")),
                            decimal(condition, "minConfidence", decimal(condition, "min_confidence", "0.0").toPlainString()).doubleValue(),
                            stringMap(firstPresent(condition, "slotEquals", "slot_equals", "slotConditions", "slot_conditions", "slots"))
                    );
                },
                envelope.tenantId(),
                publishedScene.sceneId(),
                publishedScene.version()
        );
    }

    private Map<String, Object> loadStrategy(Envelope envelope, PublishedScene publishedScene) {
        try {
            return jdbcTemplate.queryForObject("""
                            select llm_policy, model_policy, confidence_threshold
                            from nlu_strategy
                            where tenant_id = ? and scene_id = ? and version = ?
                            order by id
                            limit 1
                            """,
                    (rs, rowNum) -> {
                        Map<String, Object> strategy = new LinkedHashMap<>();
                        strategy.put("llm_policy", json(rs.getString("llm_policy")));
                        strategy.put("model_policy", json(rs.getString("model_policy")));
                        strategy.put("confidence_threshold", rs.getBigDecimal("confidence_threshold"));
                        return strategy;
                    },
                    envelope.tenantId(),
                    publishedScene.sceneId(),
                    publishedScene.version()
            );
        } catch (EmptyResultDataAccessException ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private LlmPolicy toLlmPolicy(Map<String, Object> strategy) {
        Map<String, Object> policy = (Map<String, Object>) strategy.getOrDefault("llm_policy", Map.of());
        return new LlmPolicy(
                bool(policy, "enabled", false),
                string(policy, "provider", "spring-ai-alibaba"),
                string(policy, "model", "qwen-plus"),
                integer(policy, "timeoutMs", integer(policy, "timeout_ms", 3000)),
                integer(policy, "maxRetries", integer(policy, "max_retries", 0)),
                decimal(policy, "dailyBudget", decimal(policy, "daily_budget", "0.0").toPlainString()).doubleValue(),
                string(policy, "fallbackDecision", string(policy, "fallback_decision", "REJECTED"))
        );
    }

    @SuppressWarnings("unchecked")
    private ModelPolicy toModelPolicy(Map<String, Object> strategy) {
        Map<String, Object> policy = (Map<String, Object>) strategy.getOrDefault("model_policy", Map.of());
        if (policy.isEmpty()) {
            return ModelPolicy.enabledByDefault();
        }
        BigDecimal fallbackThreshold = strategy.containsKey("confidence_threshold")
                ? new BigDecimal(strategy.get("confidence_threshold").toString())
                : BigDecimal.ZERO;
        return new ModelPolicy(
                bool(policy, "enabled", true),
                string(policy, "endpoint", string(policy, "baseUrl", string(policy, "base_url", ""))),
                integer(policy, "timeoutMs", integer(policy, "timeout_ms", 0)),
                decimal(policy, "minConfidence", decimal(policy, "min_confidence", fallbackThreshold.toPlainString()).toPlainString()).doubleValue()
        );
    }

    private Optional<PublishedScene> resolvePublishedScene(Envelope envelope) {
        Optional<String> requestedSceneId = requestedSceneId(envelope);
        if (requestedSceneId.isPresent()) {
            return findPublishedVersion(envelope.tenantId(), requestedSceneId.get())
                    .map(version -> new PublishedScene(requestedSceneId.get(), version));
        }
        return findLatestPublishedScene(envelope.tenantId())
                .or(() -> findPublishedVersion(envelope.tenantId(), DEFAULT_SCENE_ID)
                        .map(version -> new PublishedScene(DEFAULT_SCENE_ID, version)));
    }

    private Optional<String> requestedSceneId(Envelope envelope) {
        return Optional.ofNullable(envelope.metadata().get("scene_id"))
                .or(() -> Optional.ofNullable(envelope.metadata().get("sceneId")))
                .filter(value -> !value.isBlank());
    }

    private Optional<PublishedScene> findLatestPublishedScene(String tenantId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            select scene_id, version
                            from config_version
                            where tenant_id = ? and status = 'PUBLISHED'
                            order by published_at desc nulls last, id desc
                            limit 1
                            """,
                    (rs, rowNum) -> new PublishedScene(rs.getString("scene_id"), rs.getString("version")),
                    tenantId
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> findPublishedVersion(String tenantId, String sceneId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            select version
                            from config_version
                            where tenant_id = ? and scene_id = ? and status = 'PUBLISHED'
                            order by published_at desc nulls last, id desc
                            limit 1
                            """,
                    String.class,
                    tenantId,
                    sceneId
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private Map<String, Object> json(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("invalid config json", ex);
        }
    }

    private String string(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private BigDecimal decimal(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? new BigDecimal(defaultValue) : new BigDecimal(value.toString());
    }

    private int integer(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }

    private boolean bool(Map<String, Object> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private Object firstPresent(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return ((Map<String, Object>) map).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
    }

    private String inferIntentCode(String actionCode) {
        if (actionCode.endsWith("_API")) {
            return actionCode.substring(0, actionCode.length() - 4);
        }
        if (actionCode.endsWith("_SYNC")) {
            return actionCode.substring(0, actionCode.length() - 5);
        }
        if (actionCode.endsWith("_COMMAND")) {
            return actionCode.substring(0, actionCode.length() - 8);
        }
        if (actionCode.endsWith("_WEBHOOK")) {
            return actionCode.substring(0, actionCode.length() - 8);
        }
        return actionCode;
    }

    private record PublishedScene(String sceneId, String version) {
    }
}
