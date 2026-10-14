package com.wautech.crm.intelligence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiSettings {
  public final boolean enabled, retentionAcknowledged;
  public final String provider, model;
  private final String apiKey;
  public final long dailyTokens, monthlyTokens, tenantDailyTokens;

  public AiSettings(
      @Value("${AI_ENABLED:false}") boolean enabled,
      @Value("${AI_DATA_RETENTION_ACKNOWLEDGED:false}") boolean retention,
      @Value("${AI_PROVIDER:openai}") String provider,
      @Value("${AI_MODEL:}") String model,
      @Value("${AI_API_KEY:}") String apiKey,
      @Value("${AI_DAILY_TOKEN_LIMIT:250000}") long daily,
      @Value("${AI_MONTHLY_TOKEN_LIMIT:2000000}") long monthly,
      @Value("${AI_TENANT_DAILY_TOKEN_LIMIT:100000}") long tenantDaily) {
    this.enabled = enabled;
    retentionAcknowledged = retention;
    this.provider = provider;
    this.model = model;
    this.apiKey = apiKey;
    dailyTokens = daily;
    monthlyTokens = monthly;
    tenantDailyTokens = tenantDaily;
  }

  String apiKey() {
    return apiKey;
  }

  public String unavailableReason() {
    if (!enabled)
      return "AI is disabled. Ask your administrator to configure the server-side provider.";
    if (!retentionAcknowledged) return "Provider data-retention approval is required.";
    if (!provider.equals("openai")) return "The selected provider has no installed adapter.";
    if (model.startsWith("sk-") || model.equals(apiKey))
      return "Server-side AI model configuration is invalid.";
    if (model.isBlank() || !model.matches("[A-Za-z0-9._:-]{1,100}") || apiKey.isBlank())
      return "Server-side AI model or credentials are not configured.";
    if (dailyTokens < 27000 || monthlyTokens < 27000 || tenantDailyTokens < 27000)
      return "AI token budgets must permit one bounded request.";
    return "";
  }
}
