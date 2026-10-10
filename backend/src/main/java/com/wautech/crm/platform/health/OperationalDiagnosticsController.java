package com.wautech.crm.platform.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ops")
public class OperationalDiagnosticsController {
    private final DatabaseReadiness databaseReadiness;

    public OperationalDiagnosticsController(DatabaseReadiness databaseReadiness) {
        this.databaseReadiness = databaseReadiness;
    }

    @GetMapping("/health")
    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public ResponseEntity<OperationalHealthResponse> health(
            @RequestAttribute("com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext.organizationId")
            UUID organizationId) {
        boolean databaseReady = databaseReadiness.isDatabaseReady();
        return ResponseEntity.status(databaseReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new OperationalHealthResponse(databaseReady ? "UP" : "DOWN", databaseReady ? "UP" : "DOWN"));
    }

    public record OperationalHealthResponse(String status, String database) {}
}
