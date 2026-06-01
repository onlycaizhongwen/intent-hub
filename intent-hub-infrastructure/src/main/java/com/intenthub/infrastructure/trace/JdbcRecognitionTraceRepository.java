package com.intenthub.infrastructure.trace;

import com.intenthub.application.RecognitionTracePort;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcRecognitionTraceRepository implements RecognitionTracePort {
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
}
