package com.intenthub.interfaces.admin;

import com.intenthub.domain.recognition.policy.ModelClientPort;
import com.intenthub.domain.recognition.policy.ModelServiceHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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
        ModelServiceHealth modelHealth = modelClientPort.healthDetails();
        return Map.of(
                "status", "UP",
                "scope", "p1-minimal-loop",
                "model_service", modelServiceHealth(modelHealth)
        );
    }

    private Map<String, Object> modelServiceHealth(ModelServiceHealth modelHealth) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("healthy", modelHealth.healthy());
        if (modelHealth.modelVersion() != null && !modelHealth.modelVersion().isBlank()) {
            response.put("modelVersion", modelHealth.modelVersion());
        }
        if (modelHealth.threshold() != null) {
            response.put("threshold", modelHealth.threshold());
        }
        return response;
    }
}
