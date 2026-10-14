package com.wautech.crm.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.platform.security.CrmAuthorization;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@org.springframework.scheduling.annotation.EnableScheduling
public class AiService {
  static final String SESSION_KEY = AiService.class.getName() + ".conversations";

  public record Status(
      boolean available, String reason, String provider, String model, String retention) {}

  public record Turn(
      UUID requestId,
      String question,
      AiTools.Evidence evidence,
      AiProvider.Result interpretation,
      Instant at) {}

  public record History(UUID conversationId, List<Turn> turns, Instant expiresAt) {}

  static class Conversation {
    final UUID id = UUID.randomUUID(), user, org;
    final Instant expiresAt;
    final List<Turn> turns = new ArrayList<>();
    boolean busy;

    Conversation(UUID user, UUID org) {
      this(user, org, Instant.now().plusSeconds(1200));
    }

    Conversation(UUID user, UUID org, Instant expiry) {
      this.user = user;
      this.org = org;
      this.expiresAt = expiry;
    }
  }

  private final Map<HttpSession, Boolean> sessions =
      Collections.synchronizedMap(new WeakHashMap<>());
  private final AiSettings settings;
  private final AiProvider provider;
  private final AiTools tools;
  private final AiBudget budget;
  private final ObjectMapper mapper;
  private final CrmAuthorization authorization;
  private static final Logger LOG = LoggerFactory.getLogger(AiService.class);

  public AiService(
      AiSettings settings,
      AiProvider provider,
      AiTools tools,
      AiBudget budget,
      ObjectMapper mapper,
      CrmAuthorization authorization) {
    this.settings = settings;
    this.provider = provider;
    this.tools = tools;
    this.budget = budget;
    this.mapper = mapper;
    this.authorization = authorization;
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Status status(UUID org) {
    return new Status(
        settings.unavailableReason().isEmpty(),
        settings.unavailableReason(),
        settings.provider.equals("openai") ? "openai" : "unconfigured",
        settings.unavailableReason().isEmpty() ? settings.model : "",
        "Session-only history: 20 minutes, at most 10 turns; cleared on logout/expiry/reset."
            + " Provider store=false is not zero retention.");
  }

  @SuppressWarnings("unchecked")
  private Conversation conversation(UUID org, HttpSession session) {
    UUID user = authorization.currentUserId();
    if (user == null || !authorization.canView(org))
      throw new org.springframework.security.access.AccessDeniedException("Access denied");
    sessions.put(session, true);
    synchronized (session) {
      Map<UUID, Conversation> all = (Map<UUID, Conversation>) session.getAttribute(SESSION_KEY);
      if (all == null) {
        all = new HashMap<>();
        session.setAttribute(SESSION_KEY, all);
      }
      all.entrySet()
          .removeIf(
              e ->
                  !e.getValue().expiresAt.isAfter(Instant.now())
                      || !e.getValue().user.equals(user));
      var c = all.get(org);
      if (c == null) {
        if (all.size() >= 4)
          throw new AiFailure(
              HttpStatus.TOO_MANY_REQUESTS,
              "Too many active organization conversations. Clear a conversation first.");
        c = new Conversation(user, org);
        all.put(org, c);
      }
      return c;
    }
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public History history(UUID org, HttpSession session) {
    var c = conversation(org, session);
    synchronized (c) {
      return new History(c.id, List.copyOf(c.turns), c.expiresAt);
    }
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public void clear(UUID org, HttpSession session) {
    conversation(org, session);
    sessions.put(session, true);
    synchronized (session) {
      var all = (Map<?, ?>) session.getAttribute(SESSION_KEY);
      all.remove(org);
    }
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Turn ask(
      UUID org,
      HttpSession session,
      UUID conversationId,
      String question,
      AiTools.Selection selection) {
    if (!settings.unavailableReason().isEmpty())
      throw new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, settings.unavailableReason());
    if (question == null || question.isBlank() || question.length() > 1000)
      throw new AiFailure(HttpStatus.BAD_REQUEST, "Enter a question of at most 1,000 characters.");
    AiSafety.requireNoCredentials(question, settings.apiKey());
    selection.validate();
    var c = conversation(org, session);
    synchronized (c) {
      if (!c.id.equals(conversationId) || c.turns.size() >= 10)
        throw new AiFailure(
            HttpStatus.CONFLICT,
            "Conversation expired or reached 10 turns. Start a new conversation.");
      if (c.busy) throw new AiFailure(HttpStatus.CONFLICT, "A response is already in progress.");
      c.busy = true;
    }
    UUID request = UUID.randomUUID();
    long started = System.nanoTime();
    String outcome = "FAILED";
    long reserved = 0;
    try {
      var evidence = tools.read(org, selection);
      LOG.info(
          "ai_context request={} scope={} source_count={}",
          request,
          selection.scope(),
          evidence.sources().size());
      String context = mapper.writeValueAsString(evidence);
      if (context.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 11000)
        throw new AiFailure(
            HttpStatus.BAD_REQUEST, "CRM context is too large. Choose a narrower context.");
      AiSafety.requireNoCredentials(context, settings.apiKey());
      synchronized (session) {
        var current = (Map<?, ?>) session.getAttribute(SESSION_KEY);
        if (current == null || current.get(org) != c)
          throw new AiFailure(
              HttpStatus.CONFLICT,
              "Business context changed. Refresh the conversation before sharing.");
      }
      budget.reserve(org, c.user);
      reserved =
          AiBudget
              .RESERVED_TOKENS; // No refund on ambiguous/provider failure; retries consume another
      // reservation.
      var result =
          provider.answer(
              question, context, evidence.sources().stream().map(AiTools.Source::id).toList());
      if (!authorization.canView(org))
        throw new org.springframework.security.access.AccessDeniedException("Access denied");
      if (result == null
          || result.recommendations() == null
          || result.recommendations().size() > 6
          || result.limitations() == null
          || result.limitations().length() > 1500
          || result.inputTokens() < 0
          || result.inputTokens() > 26000
          || result.outputTokens() < 0
          || result.outputTokens() > 1000)
        throw new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "AI returned an unsafe response.");
      var permitted = evidence.sources().stream().map(AiTools.Source::id).toList();
      for (var rec : result.recommendations())
        if (rec.text() == null
            || rec.text().isBlank()
            || rec.text().length() > 1000
            || rec.sourceIds() == null
            || rec.sourceIds().isEmpty()
            || rec.sourceIds().size() > 8
            || !permitted.containsAll(rec.sourceIds()))
          throw new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "AI returned an unverified source.");

      AiSafety.requireNoCredentials(mapper.writeValueAsString(result), settings.apiKey());
      var turn = new Turn(request, question, evidence, result, Instant.now());
      sessions.put(session, true);
      synchronized (session) {
        var all = (Map<?, ?>) session.getAttribute(SESSION_KEY);
        if (all == null || all.get(org) != c || !c.expiresAt.isAfter(Instant.now()))
          throw new AiFailure(HttpStatus.CONFLICT, "Conversation changed. Response discarded.");
        synchronized (c) {
          c.turns.add(turn);
        }
      }
      outcome = "SUCCESS";
      LOG.info(
          "ai_usage request={} input_tokens={} output_tokens={}",
          request,
          result.inputTokens(),
          result.outputTokens());
      return turn;
    } catch (AiFailure | org.springframework.security.access.AccessDeniedException e) {
      throw e;
    } catch (Exception e) {
      throw new AiFailure(
          HttpStatus.SERVICE_UNAVAILABLE,
          "CRM context or AI provider is unavailable. No changes were made.");
    } finally {
      synchronized (c) {
        c.busy = false;
      }
      LOG.info(
          "ai_request request={} outcome={} elapsed_ms={} reserved_tokens={}",
          request,
          outcome,
          (System.nanoTime() - started) / 1000000,
          reserved);
    }
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void expireHistories() {
    List<HttpSession> snapshot;
    synchronized (sessions) {
      snapshot = new ArrayList<>(sessions.keySet());
    }
    for (var session : snapshot)
      try {
        synchronized (session) {
          var all = (Map<?, Conversation>) session.getAttribute(SESSION_KEY);
          if (all != null) {
            all.values().removeIf(c -> !c.expiresAt.isAfter(Instant.now()));
            if (all.isEmpty()) session.removeAttribute(SESSION_KEY);
          }
        }
      } catch (IllegalStateException expired) {
        sessions.remove(session);
      }
  }

  /** Invalidates copied business context on corrections, deletion or sharing revocation. */
  public void clearBusinessContext(UUID organizationId, UUID userId) {
    List<HttpSession> snapshot;
    synchronized (sessions) {
      snapshot = new ArrayList<>(sessions.keySet());
    }
    for (var session : snapshot)
      try {
        synchronized (session) {
          var all = (Map<?, Conversation>) session.getAttribute(SESSION_KEY);
          if (all != null) {
            var c = all.get(organizationId);
            if (c != null && (userId == null || c.user.equals(userId))) all.remove(organizationId);
          }
        }
      } catch (IllegalStateException expired) {
        sessions.remove(session);
      }
  }
}
