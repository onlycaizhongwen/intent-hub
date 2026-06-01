package com.intenthub.infrastructure.trace;

import com.intenthub.application.RecognitionTracePort;
import com.intenthub.application.observability.BadCaseQuery;
import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.application.observability.ObservabilityQueryPort;
import com.intenthub.application.observability.RecognitionTraceRecord;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryRecognitionTraceRepository implements RecognitionTracePort, ObservabilityQueryPort {
    private final List<IntentResult> traces = new CopyOnWriteArrayList<>();
    private final List<Envelope> envelopes = new CopyOnWriteArrayList<>();
    private final InMemoryBadCaseRepository badCaseRepository;

    public InMemoryRecognitionTraceRepository(InMemoryBadCaseRepository badCaseRepository) {
        this.badCaseRepository = badCaseRepository;
    }

    @Override
    public void record(Envelope envelope, IntentResult result) {
        envelopes.add(envelope);
        traces.add(result);
    }

    public List<IntentResult> traces() {
        return List.copyOf(traces);
    }

    @Override
    public Optional<RecognitionTraceRecord> findTraceByTraceId(String traceId) {
        for (int index = traces.size() - 1; index >= 0; index--) {
            IntentResult result = traces.get(index);
            if (result.traceId().equals(traceId)) {
                return Optional.of(toTraceRecord(envelopes.get(index), result));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<BadCaseRecord> listBadCases(BadCaseQuery query) {
        return badCaseRepository.listBadCases(query);
    }

    private RecognitionTraceRecord toTraceRecord(Envelope envelope, IntentResult result) {
        DownstreamAction action = result.downstreamAction() == null ? DownstreamAction.none() : result.downstreamAction();
        return new RecognitionTraceRecord(
                result.traceId(),
                result.requestId(),
                result.tenantId(),
                result.sceneId(),
                envelope.inputType().name(),
                Map.of(
                        "source", envelope.source(),
                        "channel", envelope.channel(),
                        "text", SensitiveDataMasker.maskText(envelope.text()),
                        "metadata", envelope.metadata(),
                        "attachments", envelope.attachments()
                ),
                result.intentCode(),
                result.decision(),
                result.confidence(),
                result.slots(),
                result.recognitionPath(),
                action.actionCode(),
                result.idempotencyKey(),
                envelope.timestamp()
        );
    }
}
