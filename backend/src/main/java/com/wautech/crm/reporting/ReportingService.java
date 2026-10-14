package com.wautech.crm.reporting;

import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.platform.search.InvalidListQueryException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates execute in PostgreSQL; never materialize complete business collections. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportingService {
  private final NamedParameterJdbcTemplate jdbc;
  private final OrganizationService organizations;

  public ReportingService(NamedParameterJdbcTemplate jdbc, OrganizationService organizations) {
    this.jdbc = jdbc;
    this.organizations = organizations;
  }

  public record Query(
      LocalDate from,
      LocalDate through,
      UUID memberId,
      UUID pipelineId,
      String currency,
      String search) {
    public void validate() {
      if (from != null && through != null && from.isAfter(through))
        throw new InvalidListQueryException("From date must precede through date");
      if ((from != null && (from.getYear() < 1900 || from.getYear() > 9998))
          || (through != null && (through.getYear() < 1900 || through.getYear() > 9998)))
        throw new InvalidListQueryException("Dates must be between 1900 and 9998");
      if (currency != null && !currency.isBlank() && !currency.matches("[A-Z]{3}"))
        throw new InvalidListQueryException("Use a three-letter currency");
      if (search != null && search.length() > 200)
        throw new InvalidListQueryException("Search must be at most 200 characters");
    }
  }

  public record Snapshot(
      Instant asOf,
      Query scope,
      Map<String, Long> counts,
      List<Map<String, Object>> leadStatuses,
      List<Map<String, Object>> opportunityStages,
      List<Map<String, Object>> taskStatuses,
      long overdueTasks,
      long completedInPeriod,
      List<Map<String, Object>> activityTypes,
      List<Map<String, Object>> recentActivities,
      List<Map<String, Object>> currencyTotals,
      List<Map<String, Object>> pipelineStages,
      boolean pipelineStagesTruncated,
      List<Map<String, Object>> ownerPerformance,
      boolean ownerPerformanceTruncated,
      List<Map<String, Object>> assigneePerformance,
      boolean assigneePerformanceTruncated) {}

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Snapshot snapshot(UUID organizationId, Query q) {
    q.validate();
    organizations.requireActiveOrganization(organizationId);
    Instant now = Instant.now();
    var p = new HashMap<String, Object>();
    p.put("org", organizationId);
    p.put("now", Timestamp.from(now));
    if (q.from() != null)
      p.put("from", Timestamp.from(q.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
    if (q.through() != null)
      p.put(
          "until",
          Timestamp.from(q.through().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
    p.put("member", q.memberId());
    p.put("pipeline", q.pipelineId());
    p.put("currency", q.currency());
    p.put(
        "search",
        q.search() == null
            ? null
            : com.wautech.crm.platform.search.SearchText.containsPattern(q.search()));
    var counts = new LinkedHashMap<String, Long>();
    for (var table : List.of("company", "contact", "lead", "opportunity", "task", "activity"))
      counts.put(
          table,
          jdbc.queryForObject(
              "select count(*) from crm."
                  + table
                  + " r"
                  + where(table, table.equals("activity") ? "occurred_at" : "created_at", q),
              p,
              Long.class));
    String leads = where("lead", "created_at", q),
        deals = where("opportunity", "created_at", q),
        tasks = where("task", "created_at", q),
        activities = where("activity", "occurred_at", q);
    var leadGroups =
        jdbc.queryForList(
            "select r.status as category,count(*) as count from crm.lead r"
                + leads
                + " group by r.status order by r.status",
            p);
    var dealGroups =
        jdbc.queryForList(
            "select r.stage as category,count(*) as count from crm.opportunity r"
                + deals
                + " group by r.stage order by r.stage",
            p);
    var taskGroups =
        jdbc.queryForList(
            "select r.status as category,count(*) as count from crm.task r"
                + tasks
                + " group by r.status order by r.status",
            p);
    long overdue =
        jdbc.queryForObject(
            "select count(*) from crm.task r"
                + tasks
                + " and r.status in ('OPEN','IN_PROGRESS') and r.due_at<=:now",
            p,
            Long.class);
    long completed =
        jdbc.queryForObject(
            "select count(*) from crm.task r"
                + where("task", "completed_at", q)
                + " and r.status='COMPLETED'",
            p,
            Long.class);
    var activityGroups =
        jdbc.queryForList(
            "select r.type as category,count(*) as count from crm.activity r"
                + activities
                + " group by r.type order by r.type",
            p);
    var recent =
        jdbc.queryForList(
            "select r.id,r.subject,r.type,r.occurred_at as \"occurredAt\" from crm.activity r"
                + activities
                + " order by r.occurred_at desc,r.id limit 10",
            p);
    var pipelineGroups =
        jdbc.queryForList(
            "select r.pipeline_id as \"pipelineId\",coalesce(r.pipeline_stage_key,r.stage) as"
                + " category,r.stage,r.currency,count(*) as count,cast(sum(r.amount) as text) as"
                + " amount from crm.opportunity r"
                + deals
                + " group by r.pipeline_id,r.pipeline_stage_key,r.stage,r.currency order by"
                + " r.pipeline_id nulls first,category,r.currency nulls first limit 2001",
            p);
    var currencyTotals =
        jdbc.queryForList(
            "select r.currency,r.stage as category,count(*) as count,cast(sum(r.amount) as text) as"
                + " amount from crm.opportunity r"
                + deals
                + " and r.currency is not null group by r.currency,r.stage order by"
                + " r.currency,r.stage",
            p);
    // Owner/assignee sections explicitly disclose their bounded group lists; global counts remain
    // complete.
    var owners =
        jdbc.queryForList(
            "select r.owner_user_id as \"userId\",r.stage as category,r.currency,count(*) as"
                + " count,cast(sum(r.amount) as text) as amount from crm.opportunity r"
                + deals
                + " group by r.owner_user_id,r.stage,r.currency order by r.owner_user_id nulls"
                + " first,r.stage,r.currency nulls first limit 201",
            p);
    var assignees =
        jdbc.queryForList(
            "select r.assignee_user_id as \"userId\",r.status as category,count(*) as count from"
                + " crm.task r"
                + tasks
                + " group by r.assignee_user_id,r.status order by r.assignee_user_id nulls"
                + " first,r.status limit 201",
            p);
    return new Snapshot(
        now,
        q,
        counts,
        leadGroups,
        dealGroups,
        taskGroups,
        overdue,
        completed,
        activityGroups,
        recent,
        currencyTotals,
        pipelineGroups.stream().limit(2000).toList(),
        pipelineGroups.size() > 2000,
        owners.stream().limit(200).toList(),
        owners.size() > 200,
        assignees.stream().limit(200).toList(),
        assignees.size() > 200);
  }

  private String where(String table, String date, Query q) {
    String result = " where r.organization_id=:org and r.archived=false";
    if (q.from() != null) result += " and r." + date + ">=:from";
    if (q.through() != null) result += " and r." + date + "<:until";
    if (table.equals("opportunity")) {
      if (q.memberId() != null) result += " and r.owner_user_id=:member";
      if (q.pipelineId() != null) result += " and r.pipeline_id=:pipeline";
      if (q.currency() != null && !q.currency().isBlank()) result += " and r.currency=:currency";
      if (q.search() != null && !q.search().isBlank())
        result += " and lower(r.name) like :search escape '\\'";
    }
    if (table.equals("task") && q.memberId() != null) result += " and r.assignee_user_id=:member";
    return result;
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public String export(UUID organizationId, Query query) {
    var s = snapshot(organizationId, query);
    var out =
        new StringBuilder(
            "\uFEFFdataset,category,count,currency,amount,user_id,pipeline_id,as_of,from_utc,through_utc,scope_member_id,scope_pipeline_id,scope_currency,scope_search\r\n");
    s.counts().forEach((key, count) -> csvRow(out, s, key, "all", count, null, null, null, null));
    for (var group :
        List.of(
            Map.entry("lead_status", s.leadStatuses()),
            Map.entry("opportunity_stage", s.opportunityStages()),
            Map.entry("task_status", s.taskStatuses()),
            Map.entry("activity_type", s.activityTypes()),
            Map.entry("currency_stage", s.currencyTotals()),
            Map.entry("pipeline_stage", s.pipelineStages()),
            Map.entry("owner", s.ownerPerformance()),
            Map.entry("assignee", s.assigneePerformance())))
      for (var row : group.getValue())
        csvRow(
            out,
            s,
            group.getKey(),
            row.get("category"),
            row.get("count"),
            row.get("currency"),
            row.get("amount"),
            row.get("userId"),
            row.get("pipelineId"));
    csvRow(out, s, "tasks", "overdue", s.overdueTasks(), null, null, null, null);
    csvRow(out, s, "tasks", "completed_in_period", s.completedInPeriod(), null, null, null, null);
    csvRow(
        out, s, "owner_groups", "truncated", s.ownerPerformanceTruncated(), null, null, null, null);
    csvRow(
        out,
        s,
        "assignee_groups",
        "truncated",
        s.assigneePerformanceTruncated(),
        null,
        null,
        null,
        null);
    csvRow(
        out,
        s,
        "pipeline_groups",
        "truncated",
        s.pipelineStagesTruncated(),
        null,
        null,
        null,
        null);
    return out.toString();
  }

  private void csvRow(StringBuilder out, Snapshot s, Object... cells) {
    var all = new ArrayList<Object>(Arrays.asList(cells));
    all.add(s.asOf());
    all.add(s.scope().from());
    all.add(s.scope().through());
    all.add(s.scope().memberId());
    all.add(s.scope().pipelineId());
    all.add(s.scope().currency());
    all.add(s.scope().search());
    out.append(
            all.stream()
                .map(ReportingService::csvCell)
                .collect(java.util.stream.Collectors.joining(",")))
        .append("\r\n");
  }

  public static String csvCell(Object value) {
    String text = value == null ? "" : value.toString();
    if (text.matches("(?s)^\\s*[=+\\-@].*")) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
