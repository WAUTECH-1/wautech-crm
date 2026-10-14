package com.wautech.crm.business;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.task.dto.TaskRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business")
public class BusinessController {
  private final BusinessContextReader reader;
  private final BusinessProfileService profiles;
  private final BusinessGoalService goals;
  private final AuthenticatedOrganizationContext context;

  public BusinessController(
      BusinessContextReader reader,
      BusinessProfileService profiles,
      BusinessGoalService goals,
      AuthenticatedOrganizationContext context) {
    this.reader = reader;
    this.profiles = profiles;
    this.goals = goals;
    this.context = context;
  }

  @GetMapping("/profile")
  public Object profile() {
    return reader.profile(context.requireOrganizationId());
  }

  @PutMapping("/profile")
  public Object saveProfile(@RequestBody BusinessInput.Profile input) {
    return profiles.saveProfile(context.requireOrganizationId(), input);
  }

  @DeleteMapping("/profile")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteProfile(@RequestParam long version) {
    profiles.deleteProfile(context.requireOrganizationId(), version);
  }

  @GetMapping("/preferences")
  public Object preferences() {
    return reader.preferences(context.requireOrganizationId());
  }

  @PutMapping("/preferences")
  public Object savePreferences(@RequestBody BusinessInput.Preferences input) {
    return profiles.savePreferences(context.requireOrganizationId(), input);
  }

  @DeleteMapping("/preferences")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePreferences(@RequestParam long version) {
    profiles.deletePreferences(context.requireOrganizationId(), version);
  }

  @GetMapping("/goals")
  public Object goals(
      @RequestParam(defaultValue = "0") int page, @RequestParam(required = false) String search) {
    return goals.list(context.requireOrganizationId(), page, search);
  }

  @GetMapping("/goals/{id}")
  public Object goal(@PathVariable UUID id) {
    return goals.get(context.requireOrganizationId(), id);
  }

  @PostMapping("/goals")
  public Object create(@RequestBody BusinessInput.Goal input) {
    return goals.create(context.requireOrganizationId(), input);
  }

  @PutMapping("/goals/{id}")
  public Object update(@PathVariable UUID id, @RequestBody BusinessInput.Goal input) {
    return goals.update(context.requireOrganizationId(), id, input);
  }

  @DeleteMapping("/goals/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, @RequestParam long version) {
    goals.delete(context.requireOrganizationId(), id, version);
  }

  public record Draft(@Min(1) long version, @NotNull @Valid TaskRequest request) {}

  public record Confirm(@NotNull UUID token) {}

  @PostMapping("/goals/{id}/milestones/{milestone}/task-preview")
  public Object preview(
      @PathVariable UUID id,
      @PathVariable UUID milestone,
      @Valid @RequestBody Draft draft,
      HttpSession session) {
    return goals.preview(
        context.requireOrganizationId(), id, milestone, draft.version(), draft.request(), session);
  }

  @PostMapping("/task-confirmation")
  public Object confirm(@Valid @RequestBody Confirm input, HttpSession session) {
    return goals.confirm(context.requireOrganizationId(), input.token(), session);
  }

  @ExceptionHandler(BusinessFailure.class)
  public ResponseEntity<ProblemDetail> failure(BusinessFailure e) {
    return ResponseEntity.status(e.status)
        .body(ProblemDetail.forStatusAndDetail(e.status, e.getMessage()));
  }
}
