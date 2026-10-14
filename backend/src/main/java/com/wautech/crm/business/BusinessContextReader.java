package com.wautech.crm.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.platform.security.CrmAuthorization;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BusinessContextReader {
  public record Document(long version, Instant updatedAt, Map<String, Object> data) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final CrmAuthorization auth;

  public BusinessContextReader(
      NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, CrmAuthorization auth) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.auth = auth;
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Document profile(UUID org) {
    return document("business_profile", Map.of("org", org));
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Document preferences(UUID org) {
    return document("business_preferences", Map.of("org", org, "user", auth.currentUserId()));
  }

  private Document document(String table, Map<String, Object> p) {
    var rows =
        jdbc.queryForList(
            "select data::text as data,version,updated_at from crm."
                + table
                + " where organization_id=:org"
                + (table.equals("business_preferences") ? " and user_id=:user" : ""),
            p);
    if (rows.isEmpty()) return new Document(0, null, null);
    var r = rows.getFirst();
    try {
      return new Document(
          ((Number) r.get("version")).longValue(),
          ((java.sql.Timestamp) r.get("updated_at")).toInstant(),
          mapper.readValue(
              r.get("data").toString(),
              new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
    } catch (Exception e) {
      throw new BusinessFailure(HttpStatus.SERVICE_UNAVAILABLE, "Business profile is unavailable.");
    }
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public Map<String, Object> advisory(UUID org) {
    var prefs = preferences(org);
    if (prefs.data() == null || !Boolean.TRUE.equals(prefs.data().get("advisorConsent")))
      throw new BusinessFailure(
          HttpStatus.BAD_REQUEST,
          "Enable advisor sharing in your private onboarding preferences first.");
    var businessProfile = profile(org);
    if (businessProfile.data() == null
        || !Boolean.TRUE.equals(businessProfile.data().get("advisorSharingAllowed")))
      throw new BusinessFailure(
          HttpStatus.BAD_REQUEST,
          "An OWNER or ADMIN must allow organization business context sharing first.");
    var result = new LinkedHashMap<String, Object>();
    result.put("provenance", "USER_PROVIDED_NOT_INDEPENDENTLY_VERIFIED");
    result.put("organizationProfile", businessProfile);
    result.put("privatePreferences", prefs);
    result.put(
        "goalStatusCounts",
        jdbc.queryForList(
            "select status,count(*) as count from crm.business_goal where organization_id=:org"
                + " group by status",
            Map.of("org", org)));
    result.put(
        "goalsSample",
        jdbc
            .queryForList(
                "select id,title,status,due_date from crm.business_goal where organization_id=:org"
                    + " order by updated_at desc,id limit 5",
                Map.of("org", org))
            .stream()
            .map(
                row -> {
                  var safe = new LinkedHashMap<String, Object>();
                  row.forEach(
                      (k, v) -> {
                        if (v != null) safe.put(k, v.toString());
                      });
                  return safe;
                })
            .toList());
    return result;
  }
}
