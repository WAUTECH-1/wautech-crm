package com.wautech.crm.identity.controller;
import com.wautech.crm.organization.dto.CurrentUserOrganizationResponse;
import com.wautech.crm.organization.service.CurrentUserOrganizationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/auth/organizations")
public class CurrentUserOrganizationController {
    private final CurrentUserOrganizationService service;
    public CurrentUserOrganizationController(CurrentUserOrganizationService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<List<CurrentUserOrganizationResponse>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());
    }
    @GetMapping("/{organizationId}/membership")
    public ResponseEntity<CurrentUserOrganizationResponse> membership(@PathVariable UUID organizationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.membership(organizationId));
    }
}
