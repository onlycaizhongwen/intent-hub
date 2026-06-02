package com.intenthub.interfaces.admin;

import com.intenthub.application.observability.BadCaseActionResult;
import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.application.observability.BadCaseTrainingSample;
import com.intenthub.application.observability.BadCaseWorkflowAppService;
import com.intenthub.application.observability.ObservabilityAppService;
import com.intenthub.application.observability.RecognitionTraceRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/observability")
public class AdminObservabilityController {
    private final ObservabilityAppService observabilityAppService;
    private final BadCaseWorkflowAppService badCaseWorkflowAppService;

    public AdminObservabilityController(ObservabilityAppService observabilityAppService, BadCaseWorkflowAppService badCaseWorkflowAppService) {
        this.observabilityAppService = observabilityAppService;
        this.badCaseWorkflowAppService = badCaseWorkflowAppService;
    }

    @GetMapping("/traces/{traceId}")
    public RecognitionTraceRecord getTrace(@PathVariable String traceId) {
        return observabilityAppService.getTrace(traceId);
    }

    @GetMapping("/bad-cases")
    public List<BadCaseRecord> listBadCases(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String sceneId,
            @RequestParam(required = false) String intentCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return observabilityAppService.listBadCases(tenantId, sceneId, intentCode, status, limit);
    }

    @PostMapping("/bad-cases/{traceId}/annotate")
    public BadCaseActionResult annotateBadCase(
            @PathVariable String traceId,
            @RequestBody BadCaseAnnotationRequest request
    ) {
        BadCaseAnnotationRequest normalized = request == null ? new BadCaseAnnotationRequest(null, null, null) : request;
        return badCaseWorkflowAppService.annotate(
                traceId,
                normalized.correctedIntentCode(),
                normalized.note(),
                normalized.actor()
        );
    }

    @PostMapping("/bad-cases/{traceId}/close")
    public BadCaseActionResult closeBadCase(
            @PathVariable String traceId,
            @RequestBody(required = false) BadCaseActionRequest request
    ) {
        BadCaseActionRequest normalized = request == null ? new BadCaseActionRequest(null, null) : request;
        return badCaseWorkflowAppService.close(traceId, normalized.note(), normalized.actor());
    }

    @GetMapping("/bad-cases/export")
    public List<BadCaseTrainingSample> exportBadCases(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String sceneId,
            @RequestParam(defaultValue = "ANNOTATED") String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean markExported,
            @RequestParam(defaultValue = "system") String actor
    ) {
        return badCaseWorkflowAppService.exportTrainingSamples(tenantId, sceneId, status, limit, markExported, actor);
    }
}
