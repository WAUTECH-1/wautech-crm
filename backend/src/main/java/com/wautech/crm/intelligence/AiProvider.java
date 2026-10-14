package com.wautech.crm.intelligence;

import java.util.List;

public interface AiProvider {
  record Recommendation(String text, List<String> sourceIds) {}

  record Result(
      List<Recommendation> recommendations,
      String limitations,
      long inputTokens,
      long outputTokens) {}

  Result answer(String question, String evidenceJson, List<String> permittedSourceIds);
}
