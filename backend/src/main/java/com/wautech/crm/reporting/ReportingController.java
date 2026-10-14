package com.wautech.crm.reporting;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {
  private final ReportingService reports;
  private final AuthenticatedOrganizationContext context;

  public ReportingController(ReportingService reports, AuthenticatedOrganizationContext context) {
    this.reports = reports;
    this.context = context;
  }

  @GetMapping("/summary")
  public ReportingService.Snapshot summary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate through,
      @RequestParam(required = false) UUID memberId,
      @RequestParam(required = false) UUID pipelineId,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String search) {
    return reports.snapshot(
        context.requireOrganizationId(),
        new ReportingService.Query(from, through, memberId, pipelineId, currency, search));
  }

  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<String> export(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate through,
      @RequestParam(required = false) UUID memberId,
      @RequestParam(required = false) UUID pipelineId,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=wautech-operational-report.csv")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(
            reports.export(
                context.requireOrganizationId(),
                new ReportingService.Query(from, through, memberId, pipelineId, currency, search)));
  }
}
