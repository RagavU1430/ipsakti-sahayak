package com.ipsakti.ip_sakti_backend.api;

import com.ipsakti.ip_sakti_backend.rag.RagClient;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final RagClient ragClient;

    public HealthController(DataSource dataSource, RagClient ragClient) {
        this.dataSource = dataSource;
        this.ragClient = ragClient;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("backend", "up");

        // Probe real database connection and inspect product metadata safely
        String dbStatus = "DOWN";
        String dbProduct = "UNKNOWN";
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                dbStatus = "UP";
                dbProduct = conn.getMetaData().getDatabaseProductName();
            }
        } catch (Exception e) {
            log.warn("Health check database probe failed: {}", e.getMessage());
        }
        response.put("db", dbStatus);
        response.put("database", dbProduct);

        // Probe RAG service health
        String ragStatus = ragClient.checkHealth() ? "UP" : "DOWN";
        response.put("rag", ragStatus);

        boolean ready = "UP".equals(dbStatus) && "UP".equals(ragStatus);
        response.put("status", ready ? "ready" : "degraded");
        if (ready) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
