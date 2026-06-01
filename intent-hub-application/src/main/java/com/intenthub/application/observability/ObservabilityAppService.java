package com.intenthub.application.observability;

import java.util.List;

public class ObservabilityAppService {
    private final ObservabilityQueryPort queryPort;

    public ObservabilityAppService(ObservabilityQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public RecognitionTraceRecord getTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        return queryPort.findTraceByTraceId(traceId)
                .orElseThrow(() -> new IllegalArgumentException("trace not found: " + traceId));
    }

    public List<BadCaseRecord> listBadCases(String tenantId, String sceneId, String intentCode, String status, int limit) {
        return queryPort.listBadCases(new BadCaseQuery(tenantId, sceneId, intentCode, status, limit));
    }
}
