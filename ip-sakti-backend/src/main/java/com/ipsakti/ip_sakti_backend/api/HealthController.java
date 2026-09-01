package com.ipsakti.ip_sakti_backend.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/health/ready")
    public Map<String, String> readiness() {
        return Map.of("status", "ready", "backend", "up");
    }
}
