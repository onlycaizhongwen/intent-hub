package com.intenthub.interfaces.admin;

import com.intenthub.domain.recognition.policy.ModelClientPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminHealthController {
    private final ModelClientPort modelClientPort;

    public AdminHealthController(ModelClientPort modelClientPort) {
        this.modelClientPort = modelClientPort;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "scope", "p1-minimal-loop",
                "model_service", Map.of("healthy", modelClientPort.healthy())
        );
    }
}
