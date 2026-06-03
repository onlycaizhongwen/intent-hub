package com.intenthub.infrastructure.llm;

import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryLlmBudgetAuditRepository implements LlmBudgetAuditPort {
    private static final String BUDGET_PROVIDER = "__budget__";
    private static final String BUDGET_MODEL = "__daily__";

    private final ConcurrentHashMap<Key, UsageCounter> counters = new ConcurrentHashMap<>();

    @Override
    public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
        double boundedUnits = Math.max(0.0, units);
        if (boundedUnits == 0.0) {
            return;
        }
        Key key = new Key(normalize(tenantId), normalize(sceneId), LocalDate.now(ZoneOffset.UTC), normalize(provider), normalize(model));
        UsageCounter counter = counters.computeIfAbsent(key, ignored -> new UsageCounter());
        counter.attempts.incrementAndGet();
        counter.consumedUnits.add(boundedUnits);
    }

    @Override
    public boolean tryReserveDailyBudget(String tenantId, String sceneId, String provider, String model, double units, double dailyBudget) {
        double boundedUnits = Math.max(0.0, units);
        double boundedBudget = Math.max(0.0, dailyBudget);
        if (boundedUnits == 0.0 || boundedBudget == 0.0 || boundedUnits > boundedBudget) {
            return false;
        }
        Key key = new Key(normalize(tenantId), normalize(sceneId), LocalDate.now(ZoneOffset.UTC), BUDGET_PROVIDER, BUDGET_MODEL);
        UsageCounter counter = counters.computeIfAbsent(key, ignored -> new UsageCounter());
        synchronized (counter) {
            if (counter.consumedUnits.sum() + boundedUnits > boundedBudget) {
                return false;
            }
            counter.attempts.incrementAndGet();
            counter.consumedUnits.add(boundedUnits);
            return true;
        }
    }

    @Override
    public void releaseDailyBudgetReservation(String tenantId, String sceneId, String provider, String model, double units) {
        double boundedUnits = Math.max(0.0, units);
        if (boundedUnits == 0.0) {
            return;
        }
        Key key = new Key(normalize(tenantId), normalize(sceneId), LocalDate.now(ZoneOffset.UTC), BUDGET_PROVIDER, BUDGET_MODEL);
        UsageCounter counter = counters.get(key);
        if (counter == null) {
            return;
        }
        synchronized (counter) {
            double currentUnits = counter.consumedUnits.sum();
            if (currentUnits <= 0.0) {
                return;
            }
            counter.consumedUnits.add(-Math.min(currentUnits, boundedUnits));
            counter.attempts.updateAndGet(value -> value > 0 ? value - 1 : 0);
        }
    }

    @Override
    public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
        LocalDate date = usageDate == null ? LocalDate.now(ZoneOffset.UTC) : usageDate;
        String normalizedTenant = normalize(tenantId);
        String normalizedScene = normalize(sceneId);
        long attempts = 0;
        double consumedUnits = 0.0;
        long reservedAttempts = 0;
        double reservedUnits = 0.0;
        for (Map.Entry<Key, UsageCounter> entry : counters.entrySet()) {
            Key key = entry.getKey();
            if (key.tenantId().equals(normalizedTenant)
                    && key.sceneId().equals(normalizedScene)
                    && key.usageDate().equals(date)) {
                UsageCounter counter = entry.getValue();
                if (BUDGET_PROVIDER.equals(key.provider())) {
                    reservedAttempts += counter.attempts.get();
                    reservedUnits += counter.consumedUnits.sum();
                } else {
                    attempts += counter.attempts.get();
                    consumedUnits += counter.consumedUnits.sum();
                }
            }
        }
        return new LlmBudgetUsage(normalizedTenant, normalizedScene, date, attempts, consumedUnits, reservedAttempts, reservedUnits);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private record Key(String tenantId, String sceneId, LocalDate usageDate, String provider, String model) {
    }

    private static final class UsageCounter {
        private final AtomicLong attempts = new AtomicLong();
        private final DoubleAdder consumedUnits = new DoubleAdder();
    }
}
