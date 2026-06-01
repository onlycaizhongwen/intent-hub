package com.intenthub.infrastructure.trace;

import com.intenthub.application.RecognitionTracePort;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryRecognitionTraceRepository implements RecognitionTracePort {
    private final List<IntentResult> traces = new CopyOnWriteArrayList<>();

    @Override
    public void record(Envelope envelope, IntentResult result) {
        traces.add(result);
    }

    public List<IntentResult> traces() {
        return List.copyOf(traces);
    }
}
