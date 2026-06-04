package com.intenthub.interfaces.admin;

import com.intenthub.application.metrics.MetricsAlertAppService;
import com.intenthub.application.metrics.MetricsAlertSnapshot;
import com.intenthub.application.metrics.MetricsAppService;
import com.intenthub.application.metrics.MetricsSnapshot;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/metrics")
public class AdminMetricsController {
    private final MetricsAppService metricsAppService;
    private final MetricsAlertAppService metricsAlertAppService;

    public AdminMetricsController(MetricsAppService metricsAppService, MetricsAlertAppService metricsAlertAppService) {
        this.metricsAppService = metricsAppService;
        this.metricsAlertAppService = metricsAlertAppService;
    }

    @GetMapping
    public MetricsSnapshot snapshot() {
        return metricsAppService.snapshot();
    }

    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prometheus() {
        return metricsAppService.prometheus();
    }

    @GetMapping("/alerts")
    public MetricsAlertSnapshot alerts() {
        return metricsAlertAppService.snapshot();
    }
}
