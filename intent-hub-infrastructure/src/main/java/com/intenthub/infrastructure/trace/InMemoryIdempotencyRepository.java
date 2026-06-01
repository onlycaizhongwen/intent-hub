package com.intenthub.infrastructure.trace;

import com.intenthub.application.IdempotencyPort;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryIdempotencyRepository implements IdempotencyPort {
    private final Set<String> reservedKeys = ConcurrentHashMap.newKeySet();

    @Override
    public String reserve(Envelope envelope, DownstreamAction action) {
        String key = IdempotencyKeyGenerator.generate(envelope, action);
        reservedKeys.add(key);
        return key;
    }

    public boolean exists(String idempotencyKey) {
        return reservedKeys.contains(idempotencyKey);
    }

}
