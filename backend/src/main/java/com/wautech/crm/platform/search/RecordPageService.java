package com.wautech.crm.platform.search;

import com.wautech.crm.activity.entity.Activity;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.note.entity.Note;
import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.task.entity.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecordPageService {
  private final EntityManager em;
  private final OrganizationService organizations;

  public RecordPageService(EntityManager e, OrganizationService o) {
    em = e;
    organizations = o;
  }

  public record RecordPage(
      List<Object> content, int page, int size, long totalElements, int totalPages) {}

  private static final Map<String, Class<?>> TYPES =
      Map.of(
          "companies",
          Company.class,
          "contacts",
          Contact.class,
          "leads",
          Lead.class,
          "opportunities",
          Opportunity.class,
          "tasks",
          Task.class,
          "activities",
          Activity.class,
          "notes",
          Note.class);
  private static final Map<String, List<String>> SORTS =
      Map.of(
          "companies",
          List.of("id", "name", "status", "createdAt", "updatedAt"),
          "contacts",
          List.of("id", "firstName", "lastName", "createdAt", "updatedAt"),
          "leads",
          List.of("id", "firstName", "lastName", "status", "createdAt", "updatedAt"),
          "opportunities",
          List.of(
              "id",
              "name",
              "amount",
              "currency",
              "stage",
              "expectedCloseDate",
              "createdAt",
              "updatedAt"),
          "tasks",
          List.of("id", "title", "status", "priority", "dueAt", "createdAt", "updatedAt"),
          "activities",
          List.of("id", "subject", "type", "occurredAt", "createdAt", "updatedAt"),
          "notes",
          List.of("id", "title", "createdAt", "updatedAt"));

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public RecordPage list(
      UUID organizationId, String resource, Map<String, String> filters, int page, int size) {
    organizations.requireActiveOrganization(organizationId);
    var type = TYPES.get(resource);
    if (type == null || page < 0 || page > 100000 || size < 1 || size > 100)
      throw new InvalidListQueryException("Invalid resource or page size");
    var sort =
        filters.getOrDefault("sortBy", resource.equals("activities") ? "occurredAt" : "createdAt");
    var direction = filters.getOrDefault("sortDirection", "desc");
    if (!SORTS.get(resource).contains(sort)
        || !List.of("asc", "desc").contains(direction.toLowerCase(Locale.ROOT)))
      throw new InvalidListQueryException("Invalid sort");
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery();
    var root = query.from(type);
    query.select(root).where(predicates(cb, root, organizationId, resource, filters));
    query.orderBy(
        direction.equalsIgnoreCase("asc") ? cb.asc(root.get(sort)) : cb.desc(root.get(sort)),
        cb.asc(root.get("id")));
    var rows =
        em.createQuery(query).setFirstResult(page * size).setMaxResults(size).getResultList();
    var count = cb.createQuery(Long.class);
    var cr = count.from(type);
    count.select(cb.count(cr)).where(predicates(cb, cr, organizationId, resource, filters));
    long total = em.createQuery(count).getSingleResult();
    return new RecordPage(
        rows.stream().map(RecordPageService::dto).toList(),
        page,
        size,
        total,
        (int) Math.ceil((double) total / size));
  }

  private Predicate[] predicates(
      CriteriaBuilder cb,
      Root<?> r,
      UUID organizationId,
      String resource,
      Map<String, String> filters) {
    var p = new ArrayList<Predicate>();
    p.add(cb.equal(r.get("organization").get("id"), organizationId));
    p.add(cb.isFalse(r.get("archived")));
    for (var entry : filters.entrySet()) {
      String key = entry.getKey(), value = entry.getValue();
      if (value == null
          || value.isBlank()
          || List.of("page", "size", "sortBy", "sortDirection").contains(key)) continue;
      try {
        if (key.equals("search")) {
          if (value.length() > 200) throw new IllegalArgumentException();
          var fields =
              resource.equals("companies") || resource.equals("opportunities")
                  ? List.of("name")
                  : resource.equals("contacts") || resource.equals("leads")
                      ? List.of("firstName", "lastName", "email")
                      : resource.equals("activities")
                          ? List.of("subject", "description")
                          : resource.equals("notes")
                              ? List.of("title", "body")
                              : List.of("title", "description");
          p.add(
              cb.or(
                  fields.stream()
                      .map(
                          f ->
                              cb.like(
                                  cb.lower(r.get(f)), SearchText.containsPattern(value), (char) 92))
                      .toArray(Predicate[]::new)));
        } else if (key.equals("currency") || key.equals("pipelineStageKey")) {
          if (!resource.equals("opportunities")
              || (key.equals("currency") && !value.matches("[A-Z]{3}"))
              || (key.equals("pipelineStageKey") && !value.matches("[a-z][a-z0-9_-]{0,49}")))
            throw new IllegalArgumentException();
          p.add(cb.equal(r.get(key), value));
        } else if (key.endsWith("Id")) {
          String relation = key.substring(0, key.length() - 2);
          var valid =
              resource.equals("companies")
                  ? List.<String>of()
                  : resource.equals("contacts") || resource.equals("leads")
                      ? List.of("company")
                      : resource.equals("opportunities")
                          ? List.of("company", "contact", "ownerUser", "pipeline")
                          : resource.equals("tasks")
                              ? List.of("company", "contact", "lead", "opportunity", "assigneeUser")
                              : List.of("company", "contact", "lead", "opportunity");
          if (!valid.contains(relation)) throw new IllegalArgumentException();
          p.add(
              cb.equal(
                  relation.equals("ownerUser")
                          || relation.equals("assigneeUser")
                          || relation.equals("pipeline")
                      ? r.get(key)
                      : r.get(relation).get("id"),
                  UUID.fromString(value)));
        } else if (key.equals("dueBefore") || key.equals("dueAfter")) {
          if (!resource.equals("tasks")) throw new IllegalArgumentException();
          p.add(
              key.equals("dueBefore")
                  ? cb.lessThanOrEqualTo(r.<Instant>get("dueAt"), Instant.parse(value))
                  : cb.greaterThanOrEqualTo(r.<Instant>get("dueAt"), Instant.parse(value)));
        } else if (key.equals("pendingOnly")) {
          if (!resource.equals("tasks") || !value.equals("true"))
            throw new IllegalArgumentException();
          p.add(
              r.get("status")
                  .in(
                      com.wautech.crm.task.entity.TaskStatus.OPEN,
                      com.wautech.crm.task.entity.TaskStatus.IN_PROGRESS));
        } else if (key.equals("overdue")) {
          if (!resource.equals("tasks") || !List.of("true", "false").contains(value))
            throw new IllegalArgumentException();
          var overdue =
              cb.and(
                  cb.lessThan(r.<Instant>get("dueAt"), Instant.now()),
                  r.get("status")
                      .in(
                          com.wautech.crm.task.entity.TaskStatus.OPEN,
                          com.wautech.crm.task.entity.TaskStatus.IN_PROGRESS));
          p.add(value.equals("true") ? overdue : cb.or(cb.isNull(r.get("dueAt")), cb.not(overdue)));
        } else {
          var allowed =
              resource.equals("leads")
                  ? List.of("status")
                  : resource.equals("opportunities")
                      ? List.of("stage")
                      : resource.equals("tasks")
                          ? List.of("status", "priority")
                          : resource.equals("activities")
                              ? List.of("type")
                              : resource.equals("companies") || resource.equals("contacts")
                                  ? List.of("status")
                                  : List.<String>of();
          if (!allowed.contains(key)) throw new IllegalArgumentException();
          p.add(cb.equal(r.get(key), enumValue(r.get(key).getJavaType(), value)));
        }
      } catch (RuntimeException invalid) {
        throw new InvalidListQueryException("Invalid filter: " + key);
      }
    }
    return p.toArray(Predicate[]::new);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object enumValue(Class<?> type, String value) {
    return type.isEnum() ? Enum.valueOf((Class) type, value) : value;
  }

  private static Object dto(Object r) {
    if (r instanceof Company x) return com.wautech.crm.company.dto.CompanyResponse.from(x);
    if (r instanceof Contact x) return com.wautech.crm.contact.dto.ContactResponse.from(x);
    if (r instanceof Lead x) return com.wautech.crm.lead.dto.LeadResponse.from(x);
    if (r instanceof Opportunity x)
      return com.wautech.crm.opportunity.dto.OpportunityResponse.from(x);
    if (r instanceof Task x) return com.wautech.crm.task.dto.TaskResponse.from(x);
    if (r instanceof Activity x) return com.wautech.crm.activity.dto.ActivityResponse.from(x);
    if (r instanceof Note x) return com.wautech.crm.note.dto.NoteResponse.from(x);
    throw new IllegalArgumentException();
  }
}
