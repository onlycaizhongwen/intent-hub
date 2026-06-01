package com.intenthub.application.observability;

public record BadCaseQuery(
        String tenantId,
        String sceneId,
        String intentCode,
        String status,
        int limit
) {
    public BadCaseQuery {
        limit = limit <= 0 ? 20 : Math.min(limit, 100);
    }
}
