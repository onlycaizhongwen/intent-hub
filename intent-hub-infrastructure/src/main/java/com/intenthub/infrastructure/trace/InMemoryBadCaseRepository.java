package com.intenthub.infrastructure.trace;

import com.intenthub.application.BadCasePort;
import com.intenthub.application.observability.BadCaseActionResult;
import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.application.observability.BadCaseTrainingSample;
import com.intenthub.application.observability.BadCaseWorkflowPort;
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
public class InMemoryBadCaseRepository implements BadCasePort, BadCaseWorkflowPort {
    private final List<IntentResult> badCases = new CopyOnWriteArrayList<>();
    private final List<Envelope> envelopes = new CopyOnWriteArrayList<>();
    private final List<String> statuses = new CopyOnWriteArrayList<>();

    @Override
    public void recordIfNeeded(Envelope envelope, IntentResult result) {
        if (result.decision() == Decision.REJECTED || result.confidence() < 0.60) {
            envelopes.add(envelope);
            badCases.add(result);
            statuses.add("OPEN");
        }
    }

    public List<IntentResult> badCases() {
        return List.copyOf(badCases);
    }

    List<BadCaseRecord> listBadCases(BadCaseQuery query) {
        return java.util.stream.IntStream.range(0, badCases.size())
                .map(index -> badCases.size() - 1 - index)
                .mapToObj(this::toBadCaseRecord)
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

    private BadCaseRecord toBadCaseRecord(int index) {
        Envelope envelope = envelopes.get(index);
        IntentResult result = badCases.get(index);
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
                statuses.get(index),
                envelope.timestamp()
        );
    }

    @Override
    public BadCaseActionResult annotate(String traceId, String correctedIntentCode, String note, String actor) {
        int index = indexOf(traceId);
        IntentResult current = badCases.get(index);
        badCases.set(index, new IntentResult(
                current.traceId(),
                current.requestId(),
                current.tenantId(),
                current.sceneId(),
                correctedIntentCode,
                current.decision(),
                current.confidence(),
                current.slots(),
                current.recognitionPath(),
                note == null || note.isBlank() ? current.message() : note,
                current.downstreamAction(),
                current.idempotencyKey()
        ));
        statuses.set(index, "ANNOTATED");
        return new BadCaseActionResult(traceId, "ANNOTATED", actor, note, java.time.Instant.now());
    }

    @Override
    public BadCaseActionResult close(String traceId, String note, String actor) {
        int index = indexOf(traceId);
        statuses.set(index, "CLOSED");
        return new BadCaseActionResult(traceId, "CLOSED", actor, note, java.time.Instant.now());
    }

    @Override
    public List<BadCaseTrainingSample> exportTrainingSamples(BadCaseQuery query, boolean markExported, String actor) {
        List<BadCaseTrainingSample> samples = java.util.stream.IntStream.range(0, badCases.size())
                .map(index -> badCases.size() - 1 - index)
                .filter(index -> matches(toBadCaseRecord(index), query))
                .limit(query.limit())
                .mapToObj(this::toTrainingSample)
                .toList();
        if (markExported) {
            samples.stream()
                    .map(BadCaseTrainingSample::traceId)
                    .mapToInt(this::indexOf)
                    .forEach(index -> statuses.set(index, "EXPORTED"));
        }
        return samples;
    }

    private BadCaseTrainingSample toTrainingSample(int index) {
        Envelope envelope = envelopes.get(index);
        IntentResult result = badCases.get(index);
        return new BadCaseTrainingSample(
                result.traceId(),
                result.requestId(),
                result.tenantId(),
                result.sceneId(),
                SensitiveDataMasker.maskText(envelope.text()),
                result.intentCode(),
                result.decision().name(),
                result.confidence(),
                result.message(),
                statuses.get(index),
                Map.of(
                        "inputType", envelope.inputType().name(),
                        "source", envelope.source(),
                        "channel", envelope.channel()
                ),
                envelope.timestamp()
        );
    }

    private int indexOf(String traceId) {
        for (int index = badCases.size() - 1; index >= 0; index--) {
            if (badCases.get(index).traceId().equals(traceId)) {
                return index;
            }
        }
        throw new IllegalArgumentException("bad case not found: " + traceId);
    }
}
