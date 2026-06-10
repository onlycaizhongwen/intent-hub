package com.intenthub.infrastructure.trace;

import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryIntentMetricsRepository implements IntentMetricsPort {
    private static final int LATENCY_SAMPLE_WINDOW = 2048;

    private final Instant startedAt = Instant.now();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalBadCases = new AtomicLong();
    private final AtomicLong totalModelFallbacks = new AtomicLong();
    private final AtomicLong totalLlmFallbacks = new AtomicLong();
    private final AtomicLong totalLlmBudgetAttempts = new AtomicLong();
    private final DoubleAdder totalLlmBudgetConsumed = new DoubleAdder();
    private final AtomicLong totalLlmBudgetReconciliations = new AtomicLong();
    private final AtomicLong totalPermissionDenied = new AtomicLong();
    private final AtomicLong totalAdminJwtAuthFailures = new AtomicLong();
    private final AtomicLong totalLatencyMillis = new AtomicLong();
    private final AtomicLong maxLatencyMillis = new AtomicLong();
    private final long[] latencySamples = new long[LATENCY_SAMPLE_WINDOW];
    private final AtomicLong latencySampleCursor = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> decisions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> intents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> scenes = new ConcurrentHashMap<>();
    private volatile Instant updatedAt = startedAt;

    @Override
    public void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis) {
        totalRequests.incrementAndGet();
        if (result.decision() == Decision.REJECTED || result.confidence() < 0.60) {
            totalBadCases.incrementAndGet();
        }
        if (result.recognitionPath().stream().anyMatch(step -> step.toUpperCase().contains("MODEL_FALLBACK"))) {
            totalModelFallbacks.incrementAndGet();
        }
        if (result.recognitionPath().stream().anyMatch(step -> step.toUpperCase().contains("LLM_FALLBACK"))) {
            totalLlmFallbacks.incrementAndGet();
        }
        long boundedLatency = Math.max(0L, latencyMillis);
        totalLatencyMillis.addAndGet(boundedLatency);
        maxLatencyMillis.accumulateAndGet(boundedLatency, Math::max);
        recordLatencySample(boundedLatency);
        increment(decisions, result.decision().name());
        increment(intents, result.intentCode());
        increment(scenes, result.sceneId());
        updatedAt = Instant.now();
    }

    @Override
    public MetricsSnapshot snapshot() {
        long requests = totalRequests.get();
        long latency = totalLatencyMillis.get();
        return new MetricsSnapshot(
                requests,
                totalBadCases.get(),
                totalModelFallbacks.get(),
                totalLlmFallbacks.get(),
                totalLlmBudgetAttempts.get(),
                totalLlmBudgetConsumed.sum(),
                totalLlmBudgetReconciliations.get(),
                totalPermissionDenied.get(),
                totalAdminJwtAuthFailures.get(),
                latency,
                requests == 0 ? 0.0 : (double) latency / requests,
                maxLatencyMillis.get(),
                percentile(0.95),
                percentile(0.99),
                snapshot(decisions),
                snapshot(intents),
                snapshot(scenes),
                startedAt,
                updatedAt
        );
    }

    @Override
    public void recordLlmBudgetConsumption(double units) {
        double boundedUnits = Math.max(0.0, units);
        if (boundedUnits == 0.0) {
            return;
        }
        totalLlmBudgetAttempts.incrementAndGet();
        totalLlmBudgetConsumed.add(boundedUnits);
        updatedAt = Instant.now();
    }

    @Override
    public void recordLlmBudgetReconciliation(int reconciledReservations) {
        if (reconciledReservations <= 0) {
            return;
        }
        totalLlmBudgetReconciliations.addAndGet(reconciledReservations);
        updatedAt = Instant.now();
    }

    @Override
    public void recordPermissionDenied(String tenantId, String sceneId, String action) {
        totalPermissionDenied.incrementAndGet();
        updatedAt = Instant.now();
    }

    @Override
    public void recordAdminJwtAuthFailure(String reason) {
        totalAdminJwtAuthFailures.incrementAndGet();
        updatedAt = Instant.now();
    }

    private void increment(ConcurrentHashMap<String, AtomicLong> counters, String key) {
        counters.computeIfAbsent(key == null || key.isBlank() ? "UNKNOWN" : key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private void recordLatencySample(long latencyMillis) {
        long cursor = latencySampleCursor.getAndIncrement();
        int index = (int) (Math.floorMod(cursor, LATENCY_SAMPLE_WINDOW));
        synchronized (latencySamples) {
            latencySamples[index] = latencyMillis;
        }
    }

    private double percentile(double quantile) {
        long seen = latencySampleCursor.get();
        int size = (int) Math.min(seen, LATENCY_SAMPLE_WINDOW);
        if (size <= 0) {
            return 0.0;
        }
        long[] copy = new long[size];
        synchronized (latencySamples) {
            System.arraycopy(latencySamples, 0, copy, 0, size);
        }
        Arrays.sort(copy);
        int index = (int) Math.ceil(quantile * size) - 1;
        index = Math.max(0, Math.min(index, size - 1));
        return copy[index];
    }

    private Map<String, Long> snapshot(ConcurrentHashMap<String, AtomicLong> counters) {
        return counters.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }
}
