package com.intenthub.application.observability;

import java.util.List;

public class BadCaseWorkflowAppService {
    private final BadCaseWorkflowPort workflowPort;

    public BadCaseWorkflowAppService(BadCaseWorkflowPort workflowPort) {
        this.workflowPort = workflowPort;
    }

    public BadCaseActionResult annotate(String traceId, String correctedIntentCode, String note, String actor) {
        requireTraceId(traceId);
        if (correctedIntentCode == null || correctedIntentCode.isBlank()) {
            throw new IllegalArgumentException("correctedIntentCode must not be blank");
        }
        return workflowPort.annotate(traceId, correctedIntentCode, normalize(note), normalizeActor(actor));
    }

    public BadCaseActionResult close(String traceId, String note, String actor) {
        requireTraceId(traceId);
        return workflowPort.close(traceId, normalize(note), normalizeActor(actor));
    }

    public List<BadCaseTrainingSample> exportTrainingSamples(String tenantId, String sceneId, String status, int limit, boolean markExported, String actor) {
        return workflowPort.exportTrainingSamples(new BadCaseQuery(tenantId, sceneId, null, status, limit), markExported, normalizeActor(actor));
    }

    private void requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private String normalizeActor(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor;
    }
}
