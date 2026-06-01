package com.intenthub.infrastructure.config;

import com.intenthub.application.SceneConfigPort;
import com.intenthub.domain.config.IntentRule;
import com.intenthub.domain.config.LlmPolicy;
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

        Map<String, DownstreamAction> actions = jdbcTemplate.queryForList("""
                        select action_code, action_type, target, idempotency_required, timeout_ms
                        from downstream_action
                        where tenant_id = ? and scene_id = ? and version = ?
                        order by id
                        """,
                envelope.tenantId(),
                publishedScene.sceneId(),
                publishedScene.version()
        ).stream().collect(Collectors.toMap(
                row -> inferIntentCode(row.get("action_code").toString()),
                row -> new DownstreamAction(
                        row.get("action_code").toString(),
                        row.get("action_type").toString(),
                        row.get("target").toString(),
                        Boolean.parseBoolean(row.get("idempotency_required").toString()),
                        Integer.parseInt(row.get("timeout_ms").toString())
                ),
                (left, right) -> left,
                LinkedHashMap::new
        ));

        return new SceneConfig(
                envelope.tenantId(),
                publishedScene.sceneId(),
                publishedScene.version(),
                0.60,
                rules.isEmpty() ? BuiltinSceneConfigFactory.orderScene(envelope).rules() : rules,
                requiredSlots,
                actions,
                LlmPolicy.disabled()
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
