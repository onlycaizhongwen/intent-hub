package com.intenthub.application.metrics;

import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;

public interface IntentMetricsPort {
    void recordRecognition(Envelope envelope, IntentResult result, long latencyMillis);

    default void recordLlmBudgetConsumption(double units) {
    }

    default void recordLlmBudgetReconciliation(int reconciledReservations) {
    }

    default void recordPermissionDenied(String tenantId, String sceneId, String action) {
    }

    default void recordAdminJwtAuthFailure(String reason) {
    }

    default void recordAdminJwksFetch() {
    }

    default void recordAdminJwksFetchFailure() {
    }

    default void recordAdminJwksCacheHit() {
    }

    default void recordAdminJwksStaleHit() {
    }

    default void recordAdminOidcDiscoveryFetch() {
    }

    default void recordAdminOidcDiscoveryFetchFailure() {
    }

    MetricsSnapshot snapshot();
}
