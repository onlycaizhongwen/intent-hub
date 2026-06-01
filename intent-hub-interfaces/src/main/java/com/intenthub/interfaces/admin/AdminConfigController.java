package com.intenthub.interfaces.admin;

import com.intenthub.application.config.ConfigBundle;
import com.intenthub.application.config.ConfigValidationResult;
import com.intenthub.application.config.ConfigVersionAppService;
import com.intenthub.application.config.ConfigVersionInfo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/config")
public class AdminConfigController {
    private final ConfigVersionAppService configVersionAppService;

    public AdminConfigController(ConfigVersionAppService configVersionAppService) {
        this.configVersionAppService = configVersionAppService;
    }

    @PostMapping("/versions")
    public ConfigVersionInfo createDraft(@Valid @RequestBody ConfigDraftRequest request) {
        return configVersionAppService.createDraft(
                request.tenantId(),
                request.sceneId(),
                request.version(),
                request.description(),
                request.normalizedActor()
        );
    }

    @GetMapping("/versions/{version}")
    public ConfigVersionInfo getVersion(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version
    ) {
        return configVersionAppService.get(tenantId, sceneId, version);
    }

    @PostMapping("/versions/{version}/validate")
    public ConfigValidationResult validate(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version
    ) {
        return configVersionAppService.validate(tenantId, sceneId, version);
    }

    @PostMapping("/versions/{version}/publish")
    public ConfigVersionInfo publish(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = request == null ? "system" : request.normalizedActor();
        return configVersionAppService.publish(tenantId, sceneId, version, actor);
    }

    @PostMapping("/versions/{version}/rollback")
    public ConfigVersionInfo rollback(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestBody(required = false) ConfigVersionActionRequest request
    ) {
        String actor = request == null ? "system" : request.normalizedActor();
        return configVersionAppService.rollback(tenantId, sceneId, version, actor);
    }

    @GetMapping("/versions/{version}/export")
    public ConfigBundle exportBundle(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @PathVariable String version,
            @RequestParam(defaultValue = "system") String actor
    ) {
        return configVersionAppService.exportBundle(tenantId, sceneId, version, actor);
    }

    @PostMapping("/versions/import")
    public ConfigVersionInfo importBundle(@Valid @RequestBody ConfigImportRequest request) {
        return configVersionAppService.importBundle(
                request.tenantId(),
                request.sceneId(),
                request.version(),
                request.bundle(),
                request.normalizedActor()
        );
    }
}
