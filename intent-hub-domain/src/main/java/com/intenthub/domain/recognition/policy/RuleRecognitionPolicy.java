package com.intenthub.domain.recognition.policy;

import com.intenthub.domain.config.IntentRule;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.RecognitionTask;

import java.util.Comparator;
import java.util.Optional;

public class RuleRecognitionPolicy implements RecognitionPolicy {
    @Override
    public Optional<RecognitionCandidate> recognize(RecognitionTask task) {
        task.markPath("RuleRecognitionPolicy");
        String text = task.envelope().text() == null ? "" : task.envelope().text();
        return task.sceneConfig().rules().stream()
                .filter(rule -> rule.matches(text))
                .max(Comparator.comparingDouble(IntentRule::confidence))
                .map(rule -> new RecognitionCandidate(
                        rule.intentCode(),
                        rule.confidence(),
                        rule.extractSlots(text),
                        rule.explanation()
                ));
    }
}
