package com.wautech.crm.business;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class BusinessContractTest {
  BusinessInput.Profile profile(boolean consent, String website, Integer team) {
    return new BusinessInput.Profile(
        0, null, null, null, website, null, team, null, null, null, consent);
  }

  @Test
  void optionalProfileQuestionsCanAllBeSkipped() {
    assertDoesNotThrow(() -> profile(true, null, null).validate());
  }

  @Test
  void storageConsentIsRequired() {
    assertThrows(BusinessFailure.class, () -> profile(false, null, null).validate());
  }

  @Test
  void websitesCannotCarryCredentialsOrPrivateQueryTokens() {
    for (String url :
        List.of(
            "javascript:alert(1)",
            "https://user:pass@example.test",
            "https://example.test?token=secret",
            "https://example.test#private",
            "not a website")) assertThrows(BusinessFailure.class, () -> BusinessInput.website(url));
    assertDoesNotThrow(() -> BusinessInput.website("https://example.test/about"));
  }

  @Test
  void teamAndTextBoundsAreEnforced() {
    assertThrows(BusinessFailure.class, () -> profile(true, null, -1).validate());
    assertThrows(
        BusinessFailure.class,
        () ->
            new BusinessInput.Profile(
                    0, "x".repeat(201), null, null, null, null, null, null, null, null, true)
                .validate());
  }

  @Test
  void allFourJourneysSupportOptionalPrivateAnswers() {
    for (String journey :
        List.of("EXISTING_OWNER", "NEW_FOUNDER", "BUSINESS_CHALLENGES", "EMPLOYEE"))
      assertDoesNotThrow(
          () ->
              new BusinessInput.Preferences(
                      0, journey, "", null, null, null, "FLEXIBLE", "STARTED", true, false)
                  .validate());
  }

  @Test
  void invalidJourneyAndProgressAreRejected() {
    assertThrows(
        BusinessFailure.class,
        () ->
            new BusinessInput.Preferences(
                    0, "ADMIN", null, null, null, null, "FLEXIBLE", "STARTED", true, false)
                .validate());
    assertThrows(
        BusinessFailure.class,
        () ->
            new BusinessInput.Preferences(
                    0, "EMPLOYEE", null, null, null, null, "WEEKLY", "FAKE", true, false)
                .validate());
  }

  @Test
  void incompleteMilestonesCannotBeClaimedAsCompletedGoal() {
    assertThrows(
        BusinessFailure.class,
        () ->
            new BusinessInput.Goal(
                    0,
                    "Goal",
                    null,
                    null,
                    "COMPLETED",
                    List.of(new BusinessInput.Milestone(null, "Step", null, false, null)))
                .validate());
  }

  @Test
  void milestonesAreBoundedAndUnique() {
    var m = new BusinessInput.Milestone(UUID.randomUUID(), "Step", null, false, null);
    assertThrows(
        BusinessFailure.class,
        () -> new BusinessInput.Goal(1, "Goal", null, null, "ACTIVE", List.of(m, m)).validate());
    assertThrows(
        BusinessFailure.class,
        () ->
            new BusinessInput.Goal(1, "Goal", null, null, "ACTIVE", Collections.nCopies(21, m))
                .validate());
  }

  @Test
  void planningDatesAndVersionsAreValidated() {
    assertThrows(
        BusinessFailure.class,
        () -> new BusinessInput.Goal(-1, "Goal", null, null, "ACTIVE", List.of()).validate());
    assertThrows(
        BusinessFailure.class,
        () ->
            new BusinessInput.Goal(0, "Goal", null, LocalDate.of(10000, 1, 1), "ACTIVE", List.of())
                .validate());
  }

  @Test
  void cancelledGoalAndCompletedMilestonesAreUserDeclarations() {
    assertDoesNotThrow(
        () ->
            new BusinessInput.Goal(
                    0,
                    "Goal",
                    null,
                    null,
                    "COMPLETED",
                    List.of(new BusinessInput.Milestone(null, "Step", null, true, null)))
                .validate());
  }
}
