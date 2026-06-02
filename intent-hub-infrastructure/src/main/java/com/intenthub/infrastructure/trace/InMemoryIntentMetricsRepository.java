package com.intenthub.infrastructure.trace;

import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryIntentMetricsRepository implements IntentMetricsPort {
    private final Instant startedAt = Instant.now();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalBadCases = new AtomicLong();
    private final AtomicLong totalLlmFallbacks = new AtomicLong();
    private final AtomicLong totalLatencyMillis = new AtomicLong();
    private final AtomicLong maxLatencyMillis = new AtomicLong();
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
        if (result.recognitionPath().stream().anyMatch(step -> step.toUpperCase().contains("LLM"))) {
            totalLlmFallbacks.incrementAndGet();
        }
        long boundedLatency = Math.max(0L, latencyMillis);
        totalLatencyMillis.addAndGet(boundedLatency);
        maxLatencyMillis.accumulateAndGet(boundedLatency, Math::max);
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
                totalLlmFallbacks.get(),
                latency,
                requests == 0 ? 0.0 : (double) latency / requests,
                maxLatencyMillis.get(),
                snapshot(decisions),
                snapshot(intents),
                snapshot(scenes),
                startedAt,
                updatedAt
        );
    }

    private void increment(ConcurrentHashMap<String, AtomicLong> counters, String key) {
        counters.computeIfAbsent(key == null || key.isBlank() ? "UNKNOWN" : key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private Map<String, Long> snapshot(ConcurrentHashMap<String, AtomicLong> counters) {
        return counters.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }
}
