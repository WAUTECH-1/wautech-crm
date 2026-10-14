package com.wautech.crm.intelligence;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/intelligence")
public class AiController {
  private final AiService service;
  private final AuthenticatedOrganizationContext context;

  public AiController(AiService service, AuthenticatedOrganizationContext context) {
    this.service = service;
    this.context = context;
  }

  public record Question(
      @NotNull UUID conversationId,
      @NotBlank @Size(max = 1000) String question,
      @NotNull @Valid AiTools.Selection selection) {}

  @GetMapping("/status")
  public AiService.Status status() {
    return service.status(context.requireOrganizationId());
  }

  @GetMapping("/conversation")
  public AiService.History history(HttpSession session) {
    return service.history(context.requireOrganizationId(), session);
  }

  @PostMapping("/conversation/reset")
  public void reset(HttpSession session) {
    service.clear(context.requireOrganizationId(), session);
  }

  @PostMapping("/messages")
  public AiService.Turn ask(@Valid @RequestBody Question input, HttpSession session) {
    return service.ask(
        context.requireOrganizationId(),
        session,
        input.conversationId(),
        input.question(),
        input.selection());
  }

  @ExceptionHandler(AiFailure.class)
  public ResponseEntity<ProblemDetail> failure(AiFailure e) {
    return ResponseEntity.status(e.status)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(ProblemDetail.forStatusAndDetail(e.status, e.getMessage()));
  }
}
