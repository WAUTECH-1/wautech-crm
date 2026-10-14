package com.wautech.crm.business;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;

public final class BusinessInput {
  public record Profile(
      long version,
      String businessName,
      String industry,
      String stage,
      String website,
      String products,
      Integer teamSize,
      String challenge,
      String shortTermGoal,
      String desiredOutcome,
      boolean storageConsent,
      boolean advisorSharingAllowed) {
    public Profile(
        long version,
        String businessName,
        String industry,
        String stage,
        String website,
        String products,
        Integer teamSize,
        String challenge,
        String shortTermGoal,
        String desiredOutcome,
        boolean storageConsent) {
      this(
          version,
          businessName,
          industry,
          stage,
          website,
          products,
          teamSize,
          challenge,
          shortTermGoal,
          desiredOutcome,
          storageConsent,
          false);
    }

    public void validate() {
      revision(version);
      consent(storageConsent);
      text(businessName, 200, false);
      text(industry, 100, false);
      text(products, 1000, false);
      text(challenge, 500, false);
      text(shortTermGoal, 500, false);
      text(desiredOutcome, 500, false);
      if (stage != null
          && !stage.isBlank()
          && !List.of("IDEA", "EARLY", "GROWTH", "ESTABLISHED", "RECOVERY").contains(stage))
        bad("Invalid business stage.");
      if (teamSize != null && (teamSize < 0 || teamSize > 1000000))
        bad("Team size must be between zero and one million.");
      BusinessInput.website(website);
    }
  }

  public record Preferences(
      long version,
      String journey,
      String userRole,
      String challenge,
      String shortTermGoal,
      String desiredOutcome,
      String planningStyle,
      String progress,
      boolean storageConsent,
      boolean advisorConsent) {
    public void validate() {
      revision(version);
      consent(storageConsent);
      if (!List.of("EXISTING_OWNER", "NEW_FOUNDER", "BUSINESS_CHALLENGES", "EMPLOYEE", "UNDECIDED")
          .contains(Objects.toString(journey, ""))) bad("Choose your journey.");
      if (!List.of("STARTED", "COMPLETE", "SKIPPED").contains(Objects.toString(progress, "")))
        bad("Invalid onboarding progress.");
      if (!List.of("DAILY", "WEEKLY", "FLEXIBLE").contains(Objects.toString(planningStyle, "")))
        bad("Choose your planning preference.");
      text(userRole, 100, false);
      text(challenge, 500, false);
      text(shortTermGoal, 500, false);
      text(desiredOutcome, 500, false);
    }
  }

  public record Milestone(
      UUID id, String title, LocalDate dueDate, boolean completed, UUID taskId) {
    public void validate() {
      text(title, 200, true);
      date(dueDate);
    }
  }

  public record Goal(
      long version,
      String title,
      String description,
      LocalDate dueDate,
      String status,
      List<Milestone> milestones) {
    public void validate() {
      revision(version);
      text(title, 200, true);
      text(description, 2000, false);
      date(dueDate);
      if (!List.of("ACTIVE", "COMPLETED", "CANCELLED").contains(Objects.toString(status, "")))
        bad("Invalid goal status.");
      if (milestones == null || milestones.size() > 20)
        bad("A goal supports at most 20 milestones.");
      var ids = new HashSet<UUID>();
      for (var m : milestones) {
        if (m == null) bad("Invalid milestone.");
        m.validate();
        if (m.id() != null && !ids.add(m.id())) bad("Duplicate milestone ID.");
      }
      if ("COMPLETED".equals(status) && milestones.stream().anyMatch(m -> !m.completed()))
        bad("Complete all milestones before completing the goal.");
    }
  }

  static void revision(long v) {
    if (v < 0 || v > Integer.MAX_VALUE) bad("Invalid version.");
  }

  static void consent(boolean value) {
    if (!value) bad("Storage consent is required. Optional answers may be omitted.");
  }

  static void text(String s, int max, boolean required) {
    if ((required && (s == null || s.isBlank())) || (s != null && s.length() > max))
      bad("Check required values and field lengths.");
  }

  static void date(LocalDate d) {
    if (d != null && (d.getYear() < 1900 || d.getYear() > 9998))
      bad("Dates must be between 1900 and 9998.");
  }

  public static void website(String value) {
    if (value == null || value.isBlank()) return;
    if (value.length() > 2000) bad("Website is too long.");
    try {
      var u = URI.create(value);
      if (!List.of("http", "https")
              .contains(Objects.toString(u.getScheme(), "").toLowerCase(Locale.ROOT))
          || u.getHost() == null
          || u.getUserInfo() != null
          || u.getQuery() != null
          || u.getFragment() != null)
        bad("Use an HTTP(S) website without credentials, query or fragment.");
    } catch (IllegalArgumentException e) {
      bad("Use a valid HTTP(S) website.");
    }
  }

  static void bad(String message) {
    throw new BusinessFailure(HttpStatus.BAD_REQUEST, message);
  }
}
