package com.intenthub.infrastructure.trace;

import com.intenthub.application.BadCasePort;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcBadCaseRepository implements BadCasePort {
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

    private String inputSnapshot(Envelope envelope) {
        return "{"
                + "\"inputType\":" + TraceJsonSupport.quote(envelope.inputType().name()) + ","
                + "\"text\":" + TraceJsonSupport.quote(SensitiveDataMasker.maskText(envelope.text()))
                + "}";
    }
}
