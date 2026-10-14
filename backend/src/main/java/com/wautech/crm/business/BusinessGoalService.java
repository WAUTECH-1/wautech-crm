package com.wautech.crm.business;

import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.intelligence.AiService;
import com.wautech.crm.platform.search.SearchText;
import com.wautech.crm.platform.security.CrmAuthorization;
import com.wautech.crm.task.dto.*;
import com.wautech.crm.task.service.TaskService;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BusinessGoalService {
  public record Milestone(
      UUID id,
      String title,
      LocalDate dueDate,
      boolean completed,
      UUID taskId,
      String taskStatus) {}

  public record Goal(
      UUID id,
      long version,
      String title,
      String description,
      LocalDate dueDate,
      String status,
      Instant updatedAt,
      List<Milestone> milestones) {}

  public record Page(List<Goal> content, int page, long totalElements, int totalPages) {}

  public record TaskPreview(UUID token, TaskRequest request, Instant expiresAt) {}

  record Pending(
      UUID org, UUID user, UUID goal, UUID milestone, long version, TaskPreview preview) {}

  static final String PREVIEWS = BusinessGoalService.class.getName() + ".previews";
  private final jakarta.persistence.EntityManager em;
  private final NamedParameterJdbcTemplate jdbc;
  private final CrmAuthorization auth;
  private final TaskService tasks;
  private final AuditEventWriter audit;
  private final AiService ai;

  public BusinessGoalService(
      NamedParameterJdbcTemplate jdbc,
      CrmAuthorization auth,
      TaskService tasks,
      AuditEventWriter audit,
      AiService ai,
      jakarta.persistence.EntityManager em) {
    this.em = em;
    this.jdbc = jdbc;
    this.auth = auth;
    this.tasks = tasks;
    this.audit = audit;
    this.ai = ai;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Page list(UUID org, int page, String search) {
    if (page < 0 || page > 10000 || search != null && search.length() > 200)
      BusinessInput.bad("Invalid goal page or search.");
    var p = new HashMap<String, Object>();
    p.put("org", org);
    p.put("offset", page * 25);
    p.put("search", SearchText.containsPattern(search));
    String where =
        " where organization_id=:org and (cast(:search as text) is null or lower(title) like"
            + " :search escape '!')";
    long total =
        jdbc.queryForObject("select count(*) from crm.business_goal" + where, p, Long.class);
    var ids =
        jdbc.queryForList(
            "select id from crm.business_goal"
                + where
                + " order by updated_at desc,id limit 25 offset :offset",
            p,
            UUID.class);
    return new Page(
        ids.stream().map(id -> get(org, id)).toList(),
        page,
        total,
        (int) Math.min(Integer.MAX_VALUE, (total + 24) / 25));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Goal get(UUID org, UUID id) {
    var rows =
        jdbc.queryForList(
            "select * from crm.business_goal where organization_id=:org and id=:id",
            Map.of("org", org, "id", id));
    if (rows.isEmpty())
      throw new BusinessFailure(HttpStatus.NOT_FOUND, "Goal unavailable in this organization.");
    var r = rows.getFirst();
    var milestones =
        jdbc
            .queryForList(
                "select m.*,t.status as task_status from crm.business_milestone m left join"
                    + " crm.task t on t.id=m.task_id and t.organization_id=m.organization_id and"
                    + " t.archived=false where m.organization_id=:org and m.goal_id=:id order by"
                    + " m.position",
                Map.of("org", org, "id", id))
            .stream()
            .map(
                m ->
                    new Milestone(
                        (UUID) m.get("id"),
                        m.get("title").toString(),
                        date(m.get("due_date")),
                        (Boolean) m.get("completed"),
                        (UUID) m.get("task_id"),
                        m.get("task_status") == null ? null : m.get("task_status").toString()))
            .toList();
    return new Goal(
        id,
        ((Number) r.get("version")).longValue(),
        r.get("title").toString(),
        (String) r.get("description"),
        date(r.get("due_date")),
        r.get("status").toString(),
        ((java.sql.Timestamp) r.get("updated_at")).toInstant(),
        milestones);
  }

  static LocalDate date(Object d) {
    return d == null ? null : ((java.sql.Date) d).toLocalDate();
  }

  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  public Goal create(UUID org, BusinessInput.Goal input) {
    input.validate();
    if (input.version() != 0 || input.milestones().stream().anyMatch(m -> m.id() != null))
      BusinessInput.bad("New goals must use new milestones.");
    UUID id = UUID.randomUUID();
    var p = params(org, id, input);
    p.put("user", auth.currentUserId());
    jdbc.update(
        "insert into"
            + " crm.business_goal(id,organization_id,title,description,due_date,status,version,created_by,created_at,updated_at)"
            + " values(:id,:org,:title,:description,:due,:status,1,:user,now(),now())",
        p);
    replaceMilestones(org, id, input.milestones(), Map.of());
    audit.recordBusiness(org, auth.currentUserId(), "BUSINESS_GOAL_CREATED", "BUSINESS_GOAL", id);
    ai.clearBusinessContext(org, null);
    return get(org, id);
  }

  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  public Goal update(UUID org, UUID id, BusinessInput.Goal input) {
    input.validate();
    var locked = lock(org, id, input.version());
    Map<UUID, UUID> existing = new HashMap<>();
    locked.milestones().forEach(m -> existing.put(m.id(), m.taskId()));
    if (jdbc.update(
            "update crm.business_goal set"
                + " title=:title,description=:description,due_date=:due,status=:status,version=version+1,updated_at=now()"
                + " where organization_id=:org and id=:id and version=:version",
            params(org, id, input))
        != 1) conflict();
    replaceMilestones(org, id, input.milestones(), existing);
    audit.recordBusiness(org, auth.currentUserId(), "BUSINESS_GOAL_UPDATED", "BUSINESS_GOAL", id);
    ai.clearBusinessContext(org, null);
    return get(org, id);
  }

  private Map<String, Object> params(UUID org, UUID id, BusinessInput.Goal input) {
    var p = new HashMap<String, Object>();
    p.put("org", org);
    p.put("id", id);
    p.put("version", input.version());
    p.put("title", input.title().trim());
    p.put("description", input.description());
    p.put("due", input.dueDate());
    p.put("status", input.status());
    return p;
  }

  private void replaceMilestones(
      UUID org, UUID goal, List<BusinessInput.Milestone> milestones, Map<UUID, UUID> existing) {
    for (var m : milestones) {
      if (m.id() != null && !existing.containsKey(m.id()))
        BusinessInput.bad("Milestone does not belong to this goal.");
      if (m.taskId() != null
          && !(m.id() != null && Objects.equals(existing.get(m.id()), m.taskId())))
        tasks.getById(org, m.taskId());
    }
    jdbc.update(
        "delete from crm.business_milestone where organization_id=:org and goal_id=:id",
        Map.of("org", org, "id", goal));
    int position = 0;
    for (var m : milestones) {
      var p = new HashMap<String, Object>();
      p.put("id", m.id() == null ? UUID.randomUUID() : m.id());
      p.put("org", org);
      p.put("goal", goal);
      p.put("title", m.title().trim());
      p.put("due", m.dueDate());
      p.put("done", m.completed());
      p.put("task", m.taskId());
      p.put("position", position++);
      jdbc.update(
          "insert into"
              + " crm.business_milestone(id,organization_id,goal_id,title,due_date,completed,task_id,position)"
              + " values(:id,:org,:goal,:title,:due,:done,:task,:position)",
          p);
    }
  }

  private Goal lock(UUID org, UUID goal, long version) {
    BusinessInput.revision(version);
    if (jdbc.queryForList(
            "select id from crm.business_goal where organization_id=:org and id=:id for update",
            Map.of("org", org, "id", goal))
        .isEmpty()) throw new BusinessFailure(HttpStatus.NOT_FOUND, "Goal unavailable.");
    var current = get(org, goal);
    if (current.version() != version) conflict();
    return current;
  }

  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  public void delete(UUID org, UUID id, long version) {
    lock(org, id, version);
    jdbc.update(
        "delete from crm.business_goal where organization_id=:org and id=:id",
        Map.of("org", org, "id", id));
    audit.recordBusiness(org, auth.currentUserId(), "BUSINESS_GOAL_DELETED", "BUSINESS_GOAL", id);
    ai.clearBusinessContext(org, null);
  }

  @SuppressWarnings("unchecked")
  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  public TaskPreview preview(
      UUID org, UUID goal, UUID milestone, long version, TaskRequest input, HttpSession session) {
    var current = lock(org, goal, version);
    if (!current.status().equals("ACTIVE"))
      BusinessInput.bad("Operational tasks require an active goal.");
    var m =
        current.milestones().stream()
            .filter(v -> v.id().equals(milestone))
            .findFirst()
            .orElseThrow(() -> new BusinessFailure(HttpStatus.NOT_FOUND, "Milestone unavailable."));
    if (m.taskId() != null) conflict();
    if (!input.isParentRecordProvided()
        || input.title() == null
        || input.title().isBlank()
        || input.title().length() > 200)
      BusinessInput.bad("Choose a CRM parent and valid task title.");
    var parents = new LinkedHashMap<String, UUID>();
    parents.put("company", input.companyId());
    parents.put("contact", input.contactId());
    parents.put("lead", input.leadId());
    parents.put("opportunity", input.opportunityId());
    for (var parent : parents.entrySet())
      if (parent.getValue() != null
          && jdbc.queryForObject(
                  "select count(*) from crm."
                      + parent.getKey()
                      + " where organization_id=:org and id=:id and archived=false",
                  Map.of("org", org, "id", parent.getValue()),
                  Long.class)
              != 1) BusinessInput.bad("Task parent unavailable in this organization.");
    var result = new TaskPreview(UUID.randomUUID(), input, Instant.now().plusSeconds(300));
    previewSessions.put(session, true);
    synchronized (session) {
      var all = (Map<UUID, Pending>) session.getAttribute(PREVIEWS);
      if (all == null) {
        all = new HashMap<>();
        session.setAttribute(PREVIEWS, all);
      }
      all.values().removeIf(p -> !p.preview().expiresAt().isAfter(Instant.now()));
      if (all.size() >= 10)
        throw new BusinessFailure(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many task previews. Wait for their five-minute expiry.");
      all.put(
          result.token(), new Pending(org, auth.currentUserId(), goal, milestone, version, result));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  @PreAuthorize("@crmAuthorization.canWrite(#p0)")
  public TaskResponse confirm(UUID org, UUID token, HttpSession session) {
    Pending pending;
    previewSessions.put(session, true);
    synchronized (session) {
      var all = (Map<UUID, Pending>) session.getAttribute(PREVIEWS);
      pending = all == null ? null : all.get(token);
      if (pending == null
          || !pending.org().equals(org)
          || !pending.user().equals(auth.currentUserId())
          || !pending.preview().expiresAt().isAfter(Instant.now())) conflict();
      all.remove(token);
    }
    var current = lock(org, pending.goal(), pending.version());
    if (!current.status().equals("ACTIVE")) conflict();
    var milestone =
        current.milestones().stream()
            .filter(m -> m.id().equals(pending.milestone()))
            .findFirst()
            .orElseThrow(() -> new BusinessFailure(HttpStatus.NOT_FOUND, "Milestone unavailable."));
    if (milestone.taskId() != null) conflict();
    var created =
        tasks.create(
            org,
            pending
                .preview()
                .request()); // Existing authorization, tenant parents and audit revalidated.
    em.flush();
    jdbc.update(
        "update crm.business_milestone set task_id=:task where organization_id=:org and id=:id",
        Map.of("org", org, "id", milestone.id(), "task", created.id()));
    jdbc.update(
        "update crm.business_goal set version=version+1,updated_at=now() where organization_id=:org"
            + " and id=:id",
        Map.of("org", org, "id", current.id()));
    audit.recordBusiness(
        org, auth.currentUserId(), "BUSINESS_GOAL_UPDATED", "BUSINESS_GOAL", current.id());
    ai.clearBusinessContext(org, null);
    return created;
  }

  static void conflict() {
    throw new BusinessFailure(
        HttpStatus.CONFLICT, "Goal or preview changed. Refresh and review again.");
  }

  private final Map<HttpSession, Boolean> previewSessions =
      Collections.synchronizedMap(new WeakHashMap<>());

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void expirePreviews() {
    List<HttpSession> sessions;
    synchronized (previewSessions) {
      sessions = new ArrayList<>(previewSessions.keySet());
    }
    for (var session : sessions)
      try {
        synchronized (session) {
          var all = (Map<?, Pending>) session.getAttribute(PREVIEWS);
          if (all != null) {
            all.values().removeIf(p -> !p.preview().expiresAt().isAfter(Instant.now()));
            if (all.isEmpty()) session.removeAttribute(PREVIEWS);
          }
        }
      } catch (IllegalStateException expired) {
        previewSessions.remove(session);
      }
  }
}
