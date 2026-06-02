package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryLlmBudgetAuditRepository implements LlmBudgetAuditPort {
    private final ConcurrentHashMap<Key, UsageCounter> counters = new ConcurrentHashMap<>();

    @Override
    public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
        double boundedUnits = Math.max(0.0, units);
        if (boundedUnits == 0.0) {
            return;
        }
        Key key = new Key(normalize(tenantId), normalize(sceneId), LocalDate.now(ZoneOffset.UTC));
        UsageCounter counter = counters.computeIfAbsent(key, ignored -> new UsageCounter());
        counter.attempts.incrementAndGet();
        counter.consumedUnits.add(boundedUnits);
    }

    @Override
    public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
        LocalDate date = usageDate == null ? LocalDate.now(ZoneOffset.UTC) : usageDate;
        Key key = new Key(normalize(tenantId), normalize(sceneId), date);
        UsageCounter counter = counters.get(key);
        if (counter == null) {
            return new LlmBudgetUsage(key.tenantId(), key.sceneId(), key.usageDate(), 0, 0.0);
        }
        return new LlmBudgetUsage(key.tenantId(), key.sceneId(), key.usageDate(), counter.attempts.get(), counter.consumedUnits.sum());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private record Key(String tenantId, String sceneId, LocalDate usageDate) {
    }

    private static final class UsageCounter {
        private final AtomicLong attempts = new AtomicLong();
        private final DoubleAdder consumedUnits = new DoubleAdder();
    }
}
