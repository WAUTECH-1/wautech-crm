package com.wautech.crm.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.intelligence.AiService;
import com.wautech.crm.platform.security.CrmAuthorization;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BusinessProfileService {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final CrmAuthorization auth;
  private final BusinessContextReader reader;
  private final AuditEventWriter audit;
  private final AiService ai;

  public BusinessProfileService(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper mapper,
      CrmAuthorization auth,
      BusinessContextReader reader,
      AuditEventWriter audit,
      AiService ai) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.auth = auth;
    this.reader = reader;
    this.audit = audit;
    this.ai = ai;
  }

  @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
  public BusinessContextReader.Document saveProfile(UUID org, BusinessInput.Profile input) {
    input.validate();
    save(org, null, input.version(), input);
    audit.recordBusiness(
        org, auth.currentUserId(), "BUSINESS_PROFILE_UPDATED", "ORGANIZATION", org);
    ai.clearBusinessContext(org, null);
    return reader.profile(org);
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public BusinessContextReader.Document savePreferences(UUID org, BusinessInput.Preferences input) {
    input.validate();
    UUID user = auth.currentUserId();
    save(org, user, input.version(), input);
    audit.recordBusiness(org, user, "BUSINESS_PREFERENCES_UPDATED", "USER", user);
    ai.clearBusinessContext(org, user);
    return reader.preferences(org);
  }

  private void save(UUID org, UUID user, long version, Object input) {
    var p = new HashMap<String, Object>();
    p.put("org", org);
    p.put("user", user);
    p.put("version", version);
    try {
      p.put("data", mapper.writeValueAsString(input));
    } catch (Exception e) {
      throw new BusinessFailure(HttpStatus.BAD_REQUEST, "Invalid profile.");
    }
    String table = user == null ? "business_profile" : "business_preferences";
    int changed;
    if (version == 0)
      changed =
          jdbc.update(
              "insert into crm."
                  + table
                  + "(organization_id,"
                  + (user == null ? "" : "user_id,")
                  + "data,version,updated_at,consented_at) values(:org,"
                  + (user == null ? "" : ":user,")
                  + "cast(:data as jsonb),1,now(),now()) on conflict do nothing",
              p);
    else
      changed =
          jdbc.update(
              "update crm."
                  + table
                  + " set data=cast(:data as"
                  + " jsonb),version=version+1,updated_at=now(),consented_at=now() where"
                  + " organization_id=:org"
                  + (user == null ? "" : " and user_id=:user")
                  + " and version=:version",
              p);
    if (changed != 1)
      throw new BusinessFailure(HttpStatus.CONFLICT, "Profile changed. Refresh before saving.");
  }

  @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
  public void deleteProfile(UUID org, long version) {
    remove(org, null, version);
    audit.recordBusiness(
        org, auth.currentUserId(), "BUSINESS_PROFILE_CLEARED", "ORGANIZATION", org);
    ai.clearBusinessContext(org, null);
  }

  @PreAuthorize("@crmAuthorization.canView(#p0)")
  public void deletePreferences(UUID org, long version) {
    UUID user = auth.currentUserId();
    remove(org, user, version);
    audit.recordBusiness(org, user, "BUSINESS_PREFERENCES_CLEARED", "USER", user);
    ai.clearBusinessContext(org, user);
  }

  private void remove(UUID org, UUID user, long version) {
    BusinessInput.revision(version);
    var p = new HashMap<String, Object>();
    p.put("org", org);
    p.put("user", user);
    p.put("version", version);
    if (jdbc.update(
            "delete from crm."
                + (user == null ? "business_profile" : "business_preferences")
                + " where organization_id=:org"
                + (user == null ? "" : " and user_id=:user")
                + " and version=:version",
            p)
        != 1)
      throw new BusinessFailure(
          HttpStatus.CONFLICT, "Profile changed or already cleared. Refresh first.");
  }
}
