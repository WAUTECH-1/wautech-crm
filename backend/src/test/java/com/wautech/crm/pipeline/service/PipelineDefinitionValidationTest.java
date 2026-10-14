package com.wautech.crm.pipeline.service;

import static org.junit.jupiter.api.Assertions.*;

import com.wautech.crm.pipeline.entity.PipelineDefinition.Stage;
import com.wautech.crm.platform.search.InvalidListQueryException;
import java.util.*;
import org.junit.jupiter.api.Test;

class PipelineDefinitionValidationTest {
  private List<Stage> stages() {
    return List.of(
        new Stage("new", "New", "OPEN"),
        new Stage("won", "Won", "WON"),
        new Stage("lost", "Lost", "LOST"));
  }

  @Test
  void acceptsOrderedOpenAndTerminalStages() {
    assertDoesNotThrow(
        () ->
            PipelineDefinitionService.validate(
                new PipelineDefinitionService.Request("Sales", stages())));
  }

  @Test
  void rejectsDuplicateKeysAndMissingTerminalOutcome() {
    assertThrows(
        InvalidListQueryException.class,
        () ->
            PipelineDefinitionService.validate(
                new PipelineDefinitionService.Request(
                    "Sales", List.of(stages().get(0), stages().get(0), stages().get(2)))));
    assertThrows(
        InvalidListQueryException.class,
        () ->
            PipelineDefinitionService.validate(
                new PipelineDefinitionService.Request(" ", stages())));
  }

  @Test
  void rejectsInvalidKeyAndNullOutcome() {
    assertThrows(
        InvalidListQueryException.class,
        () ->
            PipelineDefinitionService.validate(
                new PipelineDefinitionService.Request(
                    "Sales",
                    List.of(
                        new Stage("../bad", "Bad", "OPEN"), stages().get(1), stages().get(2)))));
    assertThrows(
        InvalidListQueryException.class,
        () ->
            PipelineDefinitionService.validate(
                new PipelineDefinitionService.Request(
                    "Sales",
                    List.of(new Stage("new", "New", null), stages().get(1), stages().get(2)))));
  }
}
