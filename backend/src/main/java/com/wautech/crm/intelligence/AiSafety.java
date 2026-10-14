package com.wautech.crm.intelligence;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Defense in depth, not a general secret or personal-data classifier. */
final class AiSafety {
  private static final Pattern CREDENTIAL =
      Pattern.compile(
          "(?i)(sk-(?:proj-|svcacct-)?[a-z0-9_-]{12,}|-----BEGIN[^\\r"
              + "\\n"
              + "]*PRIVATE KEY|(?:api[_"
              + " -]?key|password|secret|authorization|bearer)\\s*[:=]\\s*\\S+)");

  static void requireNoCredentials(String text, String configuredKey) {
    if ((configuredKey != null && !configuredKey.isBlank() && text.contains(configuredKey))
        || CREDENTIAL.matcher(text).find())
      throw new AiFailure(
          HttpStatus.BAD_REQUEST,
          "Potential credentials detected. Remove credentials from your question or selected CRM"
              + " context.");
  }
}
