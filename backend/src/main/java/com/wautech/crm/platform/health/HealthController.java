package com.wautech.crm.platform.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
public class HealthController {
    private final DatabaseReadiness databaseReadiness;

    public HealthController(DatabaseReadiness databaseReadiness) {
        this.databaseReadiness = databaseReadiness;
    }

    @GetMapping({"/api/health", "/api/health/liveness"})
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    @GetMapping("/api/health/readiness")
    public ResponseEntity<HealthResponse> readiness() {
        boolean ready = databaseReadiness.isDatabaseReady();
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(ready ? "UP" : "DOWN"));
    }

    public record HealthResponse(String status) {}
}
