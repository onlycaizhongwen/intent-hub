package com.intenthub.infrastructure.trace;

import com.intenthub.application.BadCasePort;
import com.intenthub.domain.recognition.Decision;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryBadCaseRepository implements BadCasePort {
    private final List<IntentResult> badCases = new CopyOnWriteArrayList<>();

    @Override
    public void recordIfNeeded(Envelope envelope, IntentResult result) {
        if (result.decision() == Decision.REJECTED || result.confidence() < 0.60) {
            badCases.add(result);
        }
    }

    public List<IntentResult> badCases() {
        return List.copyOf(badCases);
    }
}
