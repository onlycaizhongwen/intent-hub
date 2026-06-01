package com.intenthub.interfaces.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminHealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "scope", "p1-minimal-loop");
    }
}
