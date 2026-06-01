package com.intenthub.infrastructure.trace;

import com.intenthub.application.RecognitionTracePort;
import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.application.observability.ObservabilityQueryPort;
import com.intenthub.application.observability.RecognitionTraceRecord;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcRecognitionTraceRepository implements RecognitionTracePort, ObservabilityQueryPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRecognitionTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(Envelope envelope, IntentResult result) {
        DownstreamAction action = result.downstreamAction() == null ? DownstreamAction.none() : result.downstreamAction();
        jdbcTemplate.update("""
                        insert into recognition_trace (
                            trace_id, request_id, tenant_id, scene_id, input_type, input_snapshot,
                            intent_code, decision, confidence, slots_snapshot, recognition_path,
                            downstream_action_code, idempotency_key, created_at
                        ) values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
                        """,
                result.traceId(),
                result.requestId(),
                result.tenantId(),
                result.sceneId(),
                envelope.inputType().name(),
                inputSnapshot(envelope),
                result.intentCode(),
                result.decision().name(),
                result.confidence(),
                TraceJsonSupport.map(result.slots()),
                TraceJsonSupport.list(result.recognitionPath()),
                action.actionCode(),
                result.idempotencyKey(),
                Timestamp.from(envelope.timestamp())
        );
    }

    private String inputSnapshot(Envelope envelope) {
        return "{"
                + "\"source\":" + TraceJsonSupport.quote(envelope.source()) + ","
                + "\"channel\":" + TraceJsonSupport.quote(envelope.channel()) + ","
                + "\"text\":" + TraceJsonSupport.quote(SensitiveDataMasker.maskText(envelope.text())) + ","
                + "\"metadata\":" + TraceJsonSupport.map(envelope.metadata()) + ","
                + "\"attachments\":" + TraceJsonSupport.list(envelope.attachments())
                + "}";
    }

    @Override
    public Optional<RecognitionTraceRecord> findTraceByTraceId(String traceId) {
        List<RecognitionTraceRecord> records = jdbcTemplate.query("""
                        select trace_id, request_id, tenant_id, scene_id, input_type,
                               input_snapshot::text as input_snapshot, intent_code, decision, confidence,
                               slots_snapshot::text as slots_snapshot, recognition_path::text as recognition_path,
                               downstream_action_code, idempotency_key, created_at
                          from recognition_trace
                         where trace_id = ?
                         order by created_at desc
                         limit 1
                        """,
                (rs, rowNum) -> mapTrace(rs),
                traceId
        );
        return records.stream().findFirst();
    }

    @Override
    public List<BadCaseRecord> listBadCases(BadCaseQuery query) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select trace_id, request_id, tenant_id, scene_id, intent_code, decision,
                       confidence, reason, input_snapshot::text as input_snapshot, status, created_at
                  from bad_case
                 where 1 = 1
                """);
        addFilter(sql, args, "tenant_id", query.tenantId());
        addFilter(sql, args, "scene_id", query.sceneId());
        addFilter(sql, args, "intent_code", query.intentCode());
        addFilter(sql, args, "status", query.status());
        sql.append(" order by created_at desc limit ?");
        args.add(query.limit());
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapBadCase(rs), args.toArray());
    }

    private void addFilter(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" and ").append(column).append(" = ?");
        args.add(value);
    }

    private RecognitionTraceRecord mapTrace(ResultSet rs) throws java.sql.SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new RecognitionTraceRecord(
                rs.getString("trace_id"),
                rs.getString("request_id"),
                rs.getString("tenant_id"),
                rs.getString("scene_id"),
                rs.getString("input_type"),
                TraceJsonSupport.objectMap(rs.getString("input_snapshot")),
                rs.getString("intent_code"),
                Decision.valueOf(rs.getString("decision")),
                rs.getDouble("confidence"),
                TraceJsonSupport.stringMap(rs.getString("slots_snapshot")),
                TraceJsonSupport.stringList(rs.getString("recognition_path")),
                rs.getString("downstream_action_code"),
                rs.getString("idempotency_key"),
                createdAt == null ? null : createdAt.toInstant()
        );
    }

    private BadCaseRecord mapBadCase(ResultSet rs) throws java.sql.SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new BadCaseRecord(
                rs.getString("trace_id"),
                rs.getString("request_id"),
                rs.getString("tenant_id"),
                rs.getString("scene_id"),
                rs.getString("intent_code"),
                Decision.valueOf(rs.getString("decision")),
                rs.getDouble("confidence"),
                rs.getString("reason"),
                TraceJsonSupport.objectMap(rs.getString("input_snapshot")),
                rs.getString("status"),
                createdAt == null ? null : createdAt.toInstant()
        );
    }
}
