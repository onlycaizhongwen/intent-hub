package com.intenthub.infrastructure.trace;

import com.intenthub.application.BadCasePort;
import com.intenthub.application.observability.BadCaseActionResult;
import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseTrainingSample;
import com.intenthub.application.observability.BadCaseWorkflowPort;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcBadCaseRepository implements BadCasePort, BadCaseWorkflowPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcBadCaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordIfNeeded(Envelope envelope, IntentResult result) {
        if (result.decision() != Decision.REJECTED && result.confidence() >= 0.60) {
            return;
        }
        jdbcTemplate.update("""
                        insert into bad_case (
                            trace_id, request_id, tenant_id, scene_id, intent_code, decision,
                            confidence, reason, input_snapshot, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                        """,
                result.traceId(),
                result.requestId(),
                result.tenantId(),
                result.sceneId(),
                result.intentCode(),
                result.decision().name(),
                result.confidence(),
                result.message(),
                inputSnapshot(envelope),
                Timestamp.from(envelope.timestamp())
        );
    }

    @Override
    public BadCaseActionResult annotate(String traceId, String correctedIntentCode, String note, String actor) {
        int updated = jdbcTemplate.update("""
                        update bad_case
                           set status = 'ANNOTATED', intent_code = ?, reason = ?
                         where trace_id = ?
                        """,
                correctedIntentCode,
                note == null || note.isBlank() ? "annotated by " + actor : note,
                traceId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("bad case not found: " + traceId);
        }
        return new BadCaseActionResult(traceId, "ANNOTATED", actor, note, java.time.Instant.now());
    }

    @Override
    public BadCaseActionResult close(String traceId, String note, String actor) {
        int updated = jdbcTemplate.update("""
                        update bad_case
                           set status = 'CLOSED', reason = coalesce(nullif(?, ''), reason)
                         where trace_id = ?
                        """,
                note == null ? "" : note,
                traceId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("bad case not found: " + traceId);
        }
        return new BadCaseActionResult(traceId, "CLOSED", actor, note, java.time.Instant.now());
    }

    @Override
    public List<BadCaseTrainingSample> exportTrainingSamples(BadCaseQuery query, boolean markExported, String actor) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select trace_id, request_id, tenant_id, scene_id, intent_code, decision,
                       confidence, reason, input_snapshot::text as input_snapshot, status, created_at
                  from bad_case
                 where 1 = 1
                """);
        addFilter(sql, args, "tenant_id", query.tenantId());
        addFilter(sql, args, "scene_id", query.sceneId());
        addFilter(sql, args, "status", query.status());
        sql.append(" order by created_at desc limit ?");
        args.add(query.limit());
        List<BadCaseTrainingSample> samples = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapTrainingSample(rs), args.toArray());
        if (markExported && !samples.isEmpty()) {
            samples.forEach(sample -> jdbcTemplate.update("update bad_case set status = 'EXPORTED' where trace_id = ?", sample.traceId()));
        }
        return samples;
    }

    private String inputSnapshot(Envelope envelope) {
        return "{"
                + "\"inputType\":" + TraceJsonSupport.quote(envelope.inputType().name()) + ","
                + "\"text\":" + TraceJsonSupport.quote(SensitiveDataMasker.maskText(envelope.text()))
                + "}";
    }

    private void addFilter(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" and ").append(column).append(" = ?");
        args.add(value);
    }

    private BadCaseTrainingSample mapTrainingSample(ResultSet rs) throws java.sql.SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Map<String, Object> inputSnapshot = TraceJsonSupport.objectMap(rs.getString("input_snapshot"));
        Object text = inputSnapshot.get("text");
        return new BadCaseTrainingSample(
                rs.getString("trace_id"),
                rs.getString("request_id"),
                rs.getString("tenant_id"),
                rs.getString("scene_id"),
                text == null ? "" : text.toString(),
                rs.getString("intent_code"),
                rs.getString("decision"),
                rs.getDouble("confidence"),
                rs.getString("reason"),
                rs.getString("status"),
                inputSnapshot,
                createdAt == null ? null : createdAt.toInstant()
        );
    }
}
