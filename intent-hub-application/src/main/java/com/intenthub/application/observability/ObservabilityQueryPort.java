package com.intenthub.application.observability;

import java.util.List;
import java.util.Optional;

public interface ObservabilityQueryPort {
    Optional<RecognitionTraceRecord> findTraceByTraceId(String traceId);

    List<BadCaseRecord> listBadCases(BadCaseQuery query);
}
