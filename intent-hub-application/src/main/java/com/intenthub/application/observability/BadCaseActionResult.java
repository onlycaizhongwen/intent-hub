package com.intenthub.application.observability;

import java.time.Instant;

public record BadCaseActionResult(
        String traceId,
        String status,
        String actor,
        String note,
        Instant updatedAt
) {
}
