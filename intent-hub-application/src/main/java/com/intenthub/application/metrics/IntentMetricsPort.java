package com.intenthub.application.metrics;

import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;

public interface IntentMetricsPort {
    void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis);

    default void recordLlmBudgetConsumption(double units) {
    }

    default void recordLlmBudgetReconciliation(int reconciledReservations) {
    }

    MetricsSnapshot snapshot();
}
