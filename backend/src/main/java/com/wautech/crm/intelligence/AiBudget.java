package com.wautech.crm.intelligence;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiBudget {
  public static final long RESERVED_TOKENS = 27000;

  record Limit(String key, long increment, long maximum) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final AiSettings settings;

  public AiBudget(JdbcTemplate jdbc, PlatformTransactionManager manager, AiSettings settings) {
    this.jdbc = jdbc;
    tx = new TransactionTemplate(manager);
    this.settings = settings;
  }

  List<Limit> limits(UUID org, UUID user, Instant now) {
    var utc = now.atZone(ZoneOffset.UTC);
    return List.of(
        new Limit(
            "global:month:" + utc.getYear() + "-" + utc.getMonthValue(),
            RESERVED_TOKENS,
            settings.monthlyTokens),
        new Limit("global:day:" + utc.toLocalDate(), RESERVED_TOKENS, settings.dailyTokens),
        new Limit(
            "tenant:" + org + ":day:" + utc.toLocalDate(),
            RESERVED_TOKENS,
            settings.tenantDailyTokens),
        new Limit("tenant:" + org + ":hour:" + utc.toLocalDate() + ":" + utc.getHour(), 1, 30),
        new Limit(
            "user:"
                + user
                + ":minute:"
                + utc.toLocalDate()
                + ":"
                + utc.getHour()
                + ":"
                + utc.getMinute(),
            1,
            6));
  }

  public void reserve(UUID org, UUID user) {
    tx.executeWithoutResult(
        status -> {
          for (var limit :
              limits(org, user, Instant.now()).stream()
                  .sorted(Comparator.comparing(Limit::key))
                  .toList()) {
            jdbc.update(
                "insert into crm.ai_budget (budget_key,used) values (?,0) on conflict do nothing",
                limit.key());
            long used =
                jdbc.queryForObject(
                    "select used from crm.ai_budget where budget_key=? for update",
                    Long.class,
                    limit.key());
            if (used > limit.maximum() - limit.increment())
              throw new AiFailure(
                  HttpStatus.TOO_MANY_REQUESTS,
                  "AI request or token budget reached. Retry after the UTC rate window resets.");
            jdbc.update(
                "update crm.ai_budget set used=used+? where budget_key=?",
                limit.increment(),
                limit.key());
          }
        });
  }
}
