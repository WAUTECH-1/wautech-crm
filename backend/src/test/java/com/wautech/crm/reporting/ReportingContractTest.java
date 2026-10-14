package com.wautech.crm.reporting;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportingContractTest {
  @Test
  void rejectsReversedDates() {
    assertThrows(
        RuntimeException.class,
        () ->
            new ReportingService.Query(
                    LocalDate.of(2026, 2, 2), LocalDate.of(2026, 1, 1), null, null, null, null)
                .validate());
  }

  @Test
  void rejectsInvalidCurrencyAndOversizedSearch() {
    assertThrows(
        RuntimeException.class,
        () -> new ReportingService.Query(null, null, null, null, "USD,EUR", null).validate());
    assertThrows(
        RuntimeException.class,
        () -> new ReportingService.Query(null, null, null, null, null, "x".repeat(201)).validate());
  }

  @Test
  void acceptsOpenEndedInclusiveDateRange() {
    assertDoesNotThrow(
        () ->
            new ReportingService.Query(LocalDate.of(2026, 1, 1), null, null, null, "USD", "100%")
                .validate());
  }

  @Test
  void quotesCsvNewlinesCommasAndQuotes() {
    assertEquals("\"A,\"\"B\"\"\nC\"", ReportingService.csvCell("A,\"B\"\nC"));
  }

  @Test
  void guardsSpreadsheetFormulaAndEmptyCells() {
    assertEquals("\"'=SUM(A1)\"", ReportingService.csvCell("=SUM(A1)"));
    assertEquals("\"\"", ReportingService.csvCell(null));
  }
}
