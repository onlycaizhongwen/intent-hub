package com.intenthub.application.observability;

import java.util.List;

public interface BadCaseWorkflowPort {
    BadCaseActionResult annotate(String traceId, String correctedIntentCode, String note, String actor);

    BadCaseActionResult close(String traceId, String note, String actor);

    List<BadCaseTrainingSample> exportTrainingSamples(BadCaseQuery query, boolean markExported, String actor);
}
