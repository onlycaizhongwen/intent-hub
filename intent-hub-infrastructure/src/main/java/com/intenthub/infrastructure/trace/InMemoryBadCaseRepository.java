package com.intenthub.infrastructure.trace;

import com.intenthub.application.BadCasePort;
import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryBadCaseRepository implements BadCasePort {
    private final List<IntentResult> badCases = new CopyOnWriteArrayList<>();
    private final List<Envelope> envelopes = new CopyOnWriteArrayList<>();

    @Override
    public void recordIfNeeded(Envelope envelope, IntentResult result) {
        if (result.decision() == Decision.REJECTED || result.confidence() < 0.60) {
            envelopes.add(envelope);
            badCases.add(result);
        }
    }

    public List<IntentResult> badCases() {
        return List.copyOf(badCases);
    }

    List<BadCaseRecord> listBadCases(BadCaseQuery query) {
        return java.util.stream.IntStream.range(0, badCases.size())
                .map(index -> badCases.size() - 1 - index)
                .mapToObj(index -> toBadCaseRecord(envelopes.get(index), badCases.get(index)))
                .filter(record -> matches(record, query))
                .limit(query.limit())
                .toList();
    }

    private boolean matches(BadCaseRecord record, BadCaseQuery query) {
        return (query.tenantId() == null || query.tenantId().isBlank() || query.tenantId().equals(record.tenantId()))
                && (query.sceneId() == null || query.sceneId().isBlank() || query.sceneId().equals(record.sceneId()))
                && (query.intentCode() == null || query.intentCode().isBlank() || query.intentCode().equals(record.intentCode()))
                && (query.status() == null || query.status().isBlank() || query.status().equals(record.status()));
    }

    private BadCaseRecord toBadCaseRecord(Envelope envelope, IntentResult result) {
        return new BadCaseRecord(
                result.traceId(),
                result.requestId(),
                result.tenantId(),
                result.sceneId(),
                result.intentCode(),
                result.decision(),
                result.confidence(),
                result.message(),
                Map.of(
                        "inputType", envelope.inputType().name(),
                        "text", SensitiveDataMasker.maskText(envelope.text())
                ),
                "OPEN",
                envelope.timestamp()
        );
    }
}
