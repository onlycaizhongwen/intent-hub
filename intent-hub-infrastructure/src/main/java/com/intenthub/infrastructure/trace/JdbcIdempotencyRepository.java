package com.intenthub.infrastructure.trace;

import com.intenthub.application.IdempotencyPort;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "jdbc")
public class JdbcIdempotencyRepository implements IdempotencyPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String reserve(Envelope envelope, DownstreamAction action) {
        String key = IdempotencyKeyGenerator.generate(envelope, action);
        try {
            jdbcTemplate.update("""
                            insert into idempotency_record (
                                idempotency_key, tenant_id, request_id, request_hash, action_code,
                                action_type, target, status, retry_count, expires_at, created_at, updated_at
                            ) values (?, ?, ?, ?, ?, ?, ?, 'RESERVED', 0, ?, ?, ?)
                            """,
                    key,
                    envelope.tenantId(),
                    envelope.requestId(),
                    IdempotencyKeyGenerator.requestHash(envelope, action),
                    action.actionCode(),
                    action.actionType(),
                    action.target(),
                    Timestamp.from(envelope.timestamp().plus(Duration.ofDays(1))),
                    Timestamp.from(envelope.timestamp()),
                    Timestamp.from(envelope.timestamp())
            );
        } catch (DuplicateKeyException ignored) {
            // Idempotent reservation: the existing key is the intended outcome.
        }
        return key;
    }
}
