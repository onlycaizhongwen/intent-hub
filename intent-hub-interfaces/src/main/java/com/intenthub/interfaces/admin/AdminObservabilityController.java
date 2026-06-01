package com.intenthub.interfaces.admin;

import com.intenthub.application.observability.BadCaseRecord;
import com.intenthub.application.observability.ObservabilityAppService;
import com.intenthub.application.observability.RecognitionTraceRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/observability")
public class AdminObservabilityController {
    private final ObservabilityAppService observabilityAppService;

    public AdminObservabilityController(ObservabilityAppService observabilityAppService) {
        this.observabilityAppService = observabilityAppService;
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
}
