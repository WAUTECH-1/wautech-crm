package com.wautech.crm.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Opt-in actual HTTP/persistence tests. Exact fixture database gate prevents real account changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(
    named = "WAUTECH_TEST_DB_URL",
    matches = "jdbc:postgresql://127\\.0\\.0\\.1:15432/wautech_integration_fixture")
class UnifiedFrontendLiveTest {
  @DynamicPropertySource
  static void fixtureDatabase(DynamicPropertyRegistry properties) {
    properties.add("server.address", () -> "127.0.0.1");
    properties.add("app.reminders.enabled", () -> "false");
    String url = System.getenv("WAUTECH_TEST_DB_URL");
    if (!"jdbc:postgresql://127.0.0.1:15432/wautech_integration_fixture".equals(url))
      throw new IllegalStateException("Isolated fixture database required");
    properties.add("spring.datasource.url", () -> url);
    properties.add("spring.datasource.username", () -> "fixture");
    properties.add("spring.datasource.password", () -> System.getenv("WAUTECH_TEST_DB_PASSWORD"));
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired ObjectMapper mapper;
  UUID user, viewer, organization, second, inactive, archived;
  String email, viewerEmail, password;
  HttpClient client;
  CookieManager cookies;

  @BeforeEach
  void fixtures() throws Exception {
    user = UUID.randomUUID();
    viewer = UUID.randomUUID();
    organization = UUID.randomUUID();
    second = UUID.randomUUID();
    inactive = UUID.randomUUID();
    archived = UUID.randomUUID();
    email = user + "@fixture.example.test";
    viewerEmail = viewer + "@fixture.example.test";
    password = UUID.randomUUID().toString();
    for (UUID id : List.of(organization, second, inactive, archived))
      jdbc.update(
          "insert into crm.organization(id,name,created_at,updated_at,archived)"
              + " values(?,?,now(),now(),?)",
          id,
          "Integration fixture " + id,
          id.equals(archived));
    for (var entry : Map.of(user, email, viewer, viewerEmail).entrySet())
      jdbc.update(
          "insert into"
              + " crm.user_account(id,email,first_name,last_name,enabled,password_hash,created_at,updated_at)"
              + " values(?,?,?, ?,true,?,now(),now())",
          entry.getKey(),
          entry.getValue(),
          "Fixture",
          "User",
          encoder.encode(password));
    member(user, organization, "ACTIVE", "ADMIN");
    member(user, second, "ACTIVE", "SALES_USER");
    member(user, inactive, "REVOKED", "ADMIN");
    member(user, archived, "ACTIVE", "OWNER");
    member(viewer, organization, "ACTIVE", "VIEWER");
    cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    client = HttpClient.newBuilder().cookieHandler(cookies).build();
  }

  void member(UUID userId, UUID org, String status, String role) {
    jdbc.update(
        "insert into"
            + " crm.organization_membership(id,organization_id,user_id,status,role,joined_at,created_at,updated_at)"
            + " values(?,?,?,?,?,now(),now(),now())",
        UUID.randomUUID(),
        org,
        userId,
        status,
        role);
  }

  HttpResponse<String> request(String method, String path, Object body, UUID org, boolean csrf)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
            .header("Accept", path.startsWith("/exports/") ? "text/csv" : "application/json");
    if (org != null) builder.header("X-Organization-ID", org.toString());
    if (csrf) {
      var token = json(request("GET", "/auth/csrf", null, null, false));
      builder.header("X-CSRF-TOKEN", token.path("token").asText());
    }
    if (body != null) builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  JsonNode json(HttpResponse<String> response) throws Exception {
    return mapper.readTree(response.body());
  }

  void login(String address) throws Exception {
    assertEquals(
        200,
        request("POST", "/auth/login", Map.of("email", address, "password", password), null, true)
            .statusCode());
    assertEquals(200, request("GET", "/auth/me", null, null, false).statusCode());
  }

  @Test
  void profileAndExactMoneyPersistWithAudit() throws Exception {
    login(email);
    var response =
        request(
            "PATCH",
            "/auth/profile",
            Map.of("firstName", "Phase Two", "lastName", "Fixture"),
            null,
            true);
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(
        "Phase Two",
        jdbc.queryForObject(
            "select first_name from crm.user_account where id=?", String.class, user));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from crm.audit_event where event_type='USER_PROFILE_UPDATED' and"
                + " actor_user_id=? and target_id=?",
            Integer.class,
            user,
            user));
    UUID company =
        UUID.fromString(
            json(request("POST", "/companies", Map.of("name", "Exact fixture"), organization, true))
                .path("id")
                .asText());
    var deal =
        Map.of(
            "name",
            "Exact deal",
            "companyId",
            company,
            "amount",
            "999999999999999.1234",
            "currency",
            "USD");
    var created = request("POST", "/opportunities", deal, organization, true);
    assertEquals(201, created.statusCode(), created.body());
    var id = json(created).path("id").asText();
    assertTrue(json(created).path("amount").isTextual());
    assertEquals("999999999999999.1234", json(created).path("amount").asText());
    assertEquals(
        200, request("PUT", "/opportunities/" + id, deal, organization, true).statusCode());
    assertEquals(
        "999999999999999.1234",
        jdbc.queryForObject(
            "select amount::text from crm.opportunity where id=?",
            String.class,
            UUID.fromString(id)));
  }

  @Test
  void loginRestorationCsrfLogoutAndDisabledSession() throws Exception {
    assertEquals(401, request("GET", "/auth/me", null, null, false).statusCode());
    assertEquals(
        403,
        request("POST", "/auth/login", Map.of("email", email, "password", password), null, false)
            .statusCode());
    assertEquals(
        401,
        request(
                "POST",
                "/auth/login",
                Map.of("email", email, "password", UUID.randomUUID().toString()),
                null,
                true)
            .statusCode());
    login(email);
    assertEquals(
        user.toString(), json(request("GET", "/auth/me", null, null, false)).path("id").asText());
    assertEquals(204, request("POST", "/auth/logout", null, null, true).statusCode());
    assertEquals(401, request("GET", "/auth/me", null, null, false).statusCode());
    login(email);
    jdbc.update("update crm.user_account set enabled=false where id=?", user);
    assertEquals(401, request("GET", "/auth/me", null, null, false).statusCode());
  }

  @Test
  void discoverySelectionRevocationAndTenantIsolation() throws Exception {
    assertEquals(401, request("GET", "/auth/organizations", null, null, false).statusCode());
    login(email);
    var discovery = request("GET", "/auth/organizations", null, null, false);
    assertEquals(200, discovery.statusCode());
    assertEquals(2, json(discovery).size());
    assertEquals("no-store", discovery.headers().firstValue("cache-control").orElse(""));
    assertEquals(
        200,
        request("GET", "/auth/organizations/" + organization + "/membership", null, null, false)
            .statusCode());
    assertEquals(
        403,
        request("GET", "/auth/organizations/" + inactive + "/membership", null, null, false)
            .statusCode());
    assertEquals(
        403,
        request("GET", "/auth/organizations/" + archived + "/membership", null, null, false)
            .statusCode());
    assertEquals(
        403,
        request(
                "GET",
                "/auth/organizations/" + UUID.randomUUID() + "/membership",
                null,
                null,
                false)
            .statusCode());
    assertEquals(403, request("GET", "/companies", null, null, false).statusCode());
    var company =
        request("POST", "/companies", Map.of("name", "Isolated company"), organization, true);
    assertEquals(201, company.statusCode());
    String id = json(company).path("id").asText();
    assertEquals(404, request("GET", "/companies/" + id, null, second, false).statusCode());
    assertEquals(200, request("GET", "/companies/" + id, null, organization, false).statusCode());
    jdbc.update(
        "update crm.organization_membership set status='REVOKED' where organization_id=? and"
            + " user_id=?",
        organization,
        user);
    assertEquals(403, request("GET", "/companies", null, organization, false).statusCode());
    assertEquals(200, request("GET", "/auth/me", null, null, false).statusCode());
  }

  @Test
  void viewerCanReadButCannotWrite() throws Exception {
    login(viewerEmail);
    assertEquals(
        "VIEWER",
        json(request("GET", "/auth/organizations", null, null, false))
            .get(0)
            .path("role")
            .asText());
    assertEquals(200, request("GET", "/companies", null, organization, false).statusCode());
    assertEquals(
        403,
        request("POST", "/companies", Map.of("name", "Denied fixture"), organization, true)
            .statusCode());
    assertEquals(200, request("GET", "/auth/me", null, null, false).statusCode());
  }

  @Test
  void realCrmCrudTransitionsAndDashboardContracts() throws Exception {
    login(email);
    var company = create("companies", Map.of("name", "Fixture company"));
    String companyId = company.path("id").asText();
    var contactBody = Map.of("companyId", companyId, "firstName", "Fixture", "lastName", "Contact");
    var contact = create("contacts", contactBody);
    String contactId = contact.path("id").asText();
    var leadBody = Map.of("companyId", companyId, "firstName", "Fixture", "lastName", "Lead");
    var lead = create("leads", leadBody);
    String leadId = lead.path("id").asText();
    var opportunityBody =
        Map.of(
            "companyId",
            companyId,
            "contactId",
            contactId,
            "name",
            "Fixture deal",
            "amount",
            125.50,
            "currency",
            "USD");
    var opportunity = create("opportunities", opportunityBody);
    String opportunityId = opportunity.path("id").asText();
    for (var entry :
        Map.of(
                "companies",
                Map.of("name", "Updated fixture"),
                "contacts",
                contactBody,
                "leads",
                leadBody,
                "opportunities",
                opportunityBody)
            .entrySet()) {
      String id =
          Map.of(
                  "companies",
                  companyId,
                  "contacts",
                  contactId,
                  "leads",
                  leadId,
                  "opportunities",
                  opportunityId)
              .get(entry.getKey());
      assertEquals(
          200,
          request("GET", "/" + entry.getKey() + "/" + id, null, organization, false).statusCode());
      assertEquals(
          200,
          request("PUT", "/" + entry.getKey() + "/" + id, entry.getValue(), organization, true)
              .statusCode());
      assertTrue(
          json(request(
                  "GET",
                  "/" + entry.getKey() + "?search=Fixture&sortBy=createdAt&sortDirection=desc",
                  null,
                  organization,
                  false))
              .isArray());
    }
    assertEquals(
        200,
        request(
                "PATCH",
                "/leads/" + leadId + "/status",
                Map.of("status", "CONTACTED"),
                organization,
                true)
            .statusCode());
    assertEquals(
        200,
        request(
                "PATCH",
                "/opportunities/" + opportunityId + "/stage",
                Map.of("stage", "NEEDS_ANALYSIS"),
                organization,
                true)
            .statusCode());
    assertEquals(200, request("GET", "/pipeline", null, organization, false).statusCode());
    var tasks = request("GET", "/tasks", null, organization, false);
    assertEquals(200, tasks.statusCode(), tasks.body());
    assertTrue(json(tasks).isArray());
    var activities = request("GET", "/activities", null, organization, false);
    assertEquals(200, activities.statusCode(), activities.body());
    assertTrue(json(activities).isArray());
    var inbox = request("GET", "/notifications", null, organization, false);
    assertEquals(200, inbox.statusCode(), inbox.body());
    assertTrue(json(inbox).has("content"), inbox.body());
    var unread = request("GET", "/notifications/unread-count", null, organization, false);
    assertEquals(200, unread.statusCode(), unread.body());
    assertTrue(json(unread).has("count"), unread.body());
    for (var entry :
        List.of(
            Map.entry("opportunities", opportunityId),
            Map.entry("leads", leadId),
            Map.entry("contacts", contactId),
            Map.entry("companies", companyId))) {
      assertEquals(
          204,
          request("DELETE", "/" + entry.getKey() + "/" + entry.getValue(), null, organization, true)
              .statusCode());
      assertEquals(
          404,
          request("GET", "/" + entry.getKey() + "/" + entry.getValue(), null, organization, false)
              .statusCode());
    }
  }

  JsonNode create(String resource, Object body) throws Exception {
    var response = request("POST", "/" + resource, body, organization, true);
    assertEquals(201, response.statusCode(), response.body());
    return json(response);
  }

  UUID companyFixture() throws Exception {
    return UUID.fromString(
        json(request("POST", "/companies", Map.of("name", "Phase two fixture"), organization, true))
            .path("id")
            .asText());
  }

  UUID taskFixture(UUID company) throws Exception {
    var created =
        request(
            "POST",
            "/tasks",
            Map.of(
                "companyId",
                company,
                "title",
                "Due follow-up",
                "priority",
                "HIGH",
                "dueAt",
                java.time.Instant.now().minusSeconds(60).toString()),
            organization,
            true);
    assertEquals(201, created.statusCode(), created.body());
    return UUID.fromString(json(created).path("id").asText());
  }

  UUID dealFixture(UUID company) throws Exception {
    var created =
        request(
            "POST",
            "/opportunities",
            Map.of(
                "companyId",
                company,
                "name",
                "Phase two deal",
                "amount",
                "0.1000",
                "currency",
                "USD"),
            organization,
            true);
    assertEquals(201, created.statusCode(), created.body());
    return UUID.fromString(json(created).path("id").asText());
  }

  @Test
  void assignmentsRejectForeignInactiveAndViewerWritesAndGenerateOwnDueReminderOnce()
      throws Exception {
    login(email);
    UUID company = companyFixture(), task = taskFixture(company), deal = dealFixture(company);
    assertEquals(
        200,
        request("GET", "/organization/eligible-members", null, organization, false).statusCode());
    var assigned =
        request(
            "PATCH", "/tasks/" + task + "/assignee", Map.of("userId", viewer), organization, true);
    assertEquals(200, assigned.statusCode(), assigned.body());
    assertEquals(viewer.toString(), json(assigned).path("assigneeUserId").asText());
    assertEquals(
        400,
        request(
                "PATCH",
                "/tasks/" + task + "/assignee",
                Map.of("userId", UUID.randomUUID()),
                organization,
                true)
            .statusCode());
    assertEquals(
        400,
        request("PATCH", "/tasks/" + task + "/assignee", Map.of("userId", viewer), second, true)
            .statusCode());
    assertEquals(
        200,
        request(
                "PATCH",
                "/opportunities/" + deal + "/owner",
                Map.of("userId", user),
                organization,
                true)
            .statusCode());
    assertEquals(
        user,
        jdbc.queryForObject(
            "select owner_user_id from crm.opportunity where id=?", UUID.class, deal));
    login(viewerEmail);
    assertEquals(
        403,
        request("PATCH", "/tasks/" + task + "/assignee", Map.of("userId", user), organization, true)
            .statusCode());
    var reminders = request("POST", "/tasks/reminders/check", null, organization, true);
    assertEquals(200, reminders.statusCode(), reminders.body());
    assertEquals(1, json(reminders).path("checkedCount").asInt());
    assertEquals(
        0,
        json(request("POST", "/tasks/reminders/check", null, organization, true))
            .path("checkedCount")
            .asInt());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from crm.notification where notification_type='TASK_DUE' and"
                + " target_id=? and recipient_user_id=?",
            Integer.class,
            task,
            viewer));
    String dueNotification =
        jdbc.queryForObject(
            "select id::text from crm.notification where notification_type='TASK_DUE' and"
                + " target_id=? and recipient_user_id=?",
            String.class,
            task,
            viewer);
    assertEquals(
        200,
        request("PATCH", "/notifications/" + dueNotification + "/read", null, organization, true)
            .statusCode());
    var unread =
        request("PATCH", "/notifications/" + dueNotification + "/unread", null, organization, true);
    assertEquals(200, unread.statusCode(), unread.body());
    assertTrue(json(unread).path("readAt").isNull());
    login(email);
    assertEquals(
        404,
        request("PATCH", "/notifications/" + dueNotification + "/unread", null, organization, true)
            .statusCode());
    jdbc.update(
        "update crm.organization_membership set status='SUSPENDED' where organization_id=? and"
            + " user_id=?",
        organization,
        viewer);
    assertEquals(
        400,
        request(
                "PATCH",
                "/tasks/" + task + "/assignee",
                Map.of("userId", viewer),
                organization,
                true)
            .statusCode());
    assertEquals(
        viewer,
        jdbc.queryForObject("select assignee_user_id from crm.task where id=?", UUID.class, task));
  }

  @Test
  void serverPaginationIsBoundedFilteredAndTenantScoped() throws Exception {
    login(email);
    UUID company = companyFixture();
    companyFixture();
    var response =
        request(
            "GET",
            "/records/companies?page=0&size=1&sortBy=name&sortDirection=asc&search=Phase",
            null,
            organization,
            false);
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(2, json(response).path("totalElements").asInt());
    assertEquals(1, json(response).path("content").size());
    assertEquals(
        0,
        json(request("GET", "/records/companies?size=1", null, second, false))
            .path("totalElements")
            .asInt());
    assertEquals(
        400, request("GET", "/records/companies?size=101", null, organization, false).statusCode());
    assertEquals(
        400,
        request("GET", "/records/companies?sortBy=passwordHash", null, organization, false)
            .statusCode());
    assertEquals(
        400,
        request("GET", "/records/companies?userId=" + user, null, organization, false)
            .statusCode());
    assertEquals(403, request("GET", "/records/companies", null, inactive, false).statusCode());
    var archived = request("DELETE", "/companies/" + company, null, organization, true);
    assertEquals(204, archived.statusCode());
    assertEquals(
        1,
        json(request("GET", "/records/companies", null, organization, false))
            .path("totalElements")
            .asInt());
  }

  @Test
  void notesCrudAndSavedViewApplicationUseActualDatabaseAndRoleGuards() throws Exception {
    login(email);
    UUID company = companyFixture();
    var note =
        Map.of("companyId", company, "title", "Meeting notes", "body", "Confirmed requirements");
    var created = request("POST", "/notes", note, organization, true);
    assertEquals(201, created.statusCode(), created.body());
    String id = json(created).path("id").asText();
    assertEquals(
        200,
        request(
                "PUT",
                "/notes/" + id,
                Map.of(
                    "companyId", company, "title", "Updated notes", "body", "Updated requirements"),
                organization,
                true)
            .statusCode());
    assertEquals(
        1,
        json(request("GET", "/records/notes?companyId=" + company, null, organization, false))
            .path("totalElements")
            .asInt());
    assertEquals(404, request("GET", "/notes/" + id, null, second, false).statusCode());
    assertEquals(
        400,
        request(
                "POST",
                "/notes",
                Map.of(
                    "companyId",
                    company,
                    "leadId",
                    UUID.randomUUID(),
                    "title",
                    "Bad",
                    "body",
                    "Bad"),
                organization,
                true)
            .statusCode());
    var view =
        Map.of(
            "name",
            "Phase companies",
            "resource",
            "COMPANY",
            "configurationVersion",
            1,
            "configuration",
            Map.of("search", "Phase", "sortBy", "name", "sortDirection", "asc"));
    var saved = request("POST", "/saved-views", view, organization, true);
    assertEquals(201, saved.statusCode(), saved.body());
    String viewId = json(saved).path("id").asText();
    assertEquals(
        200, request("PUT", "/saved-views/" + viewId, view, organization, true).statusCode());
    assertEquals(
        1,
        json(request(
                "GET", "/records/companies?search=Phase&sortBy=name", null, organization, false))
            .path("totalElements")
            .asInt());
    assertEquals(404, request("GET", "/saved-views/" + viewId, null, second, false).statusCode());
    login(viewerEmail);
    assertEquals(200, request("GET", "/notes/" + id, null, organization, false).statusCode());
    assertEquals(403, request("DELETE", "/notes/" + id, null, organization, true).statusCode());
    assertEquals(403, request("POST", "/saved-views", view, organization, true).statusCode());
    login(email);
    assertEquals(204, request("DELETE", "/notes/" + id, null, organization, true).statusCode());
    assertEquals(
        204, request("DELETE", "/saved-views/" + viewId, null, organization, true).statusCode());
    assertEquals(
        0,
        json(request("GET", "/records/notes", null, organization, false))
            .path("totalElements")
            .asInt());
  }

  HttpResponse<String> csvRequest(String path, String csv) throws Exception {
    String boundary = "fixture-" + UUID.randomUUID(),
        token = json(request("GET", "/auth/csrf", null, null, false)).path("token").asText();
    String body =
        "--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"fixture.csv\"\r\n"
            + "Content-Type: text/csv\r\n\r\n"
            + csv
            + "\r\n--"
            + boundary
            + "--\r\n";
    var req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
            .header("X-Organization-ID", organization.toString())
            .header("X-CSRF-TOKEN", token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return client.send(req, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void csvPreviewAtomicImportAndSafeExportWorkThroughHttp() throws Exception {
    login(email);
    String header = "name,website,industry,phone,email,status\n",
        good = header + "=2+3,,,,,ACTIVE\n",
        bad = header + ",,,,,ACTIVE\n";
    var preview = csvRequest("/imports/companies/validate", good);
    assertEquals(200, preview.statusCode(), preview.body());
    assertEquals(1, json(preview).path("validRows").asInt());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from crm.company where organization_id=?",
            Integer.class,
            organization));
    assertEquals(1, json(csvRequest("/imports/companies", good)).path("createdRecords").asInt());
    assertEquals(400, csvRequest("/imports/companies", bad).statusCode());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from crm.company where organization_id=?",
            Integer.class,
            organization));
    var exported = request("GET", "/exports/companies", null, organization, false);
    assertEquals(200, exported.statusCode(), exported.body());
    assertTrue(exported.body().contains("'=2+3"));
    login(viewerEmail);
    assertEquals(403, csvRequest("/imports/companies/validate", good).statusCode());
    assertEquals(200, request("GET", "/exports/companies", null, organization, false).statusCode());
  }

  List<Map<String, String>> stageFixture() {
    return List.of(
        Map.of("key", "discovery", "name", "Discovery", "outcome", "OPEN"),
        Map.of("key", "proposal", "name", "Proposal", "outcome", "OPEN"),
        Map.of("key", "won", "name", "Won", "outcome", "WON"),
        Map.of("key", "lost", "name", "Lost", "outcome", "LOST"));
  }

  @Test
  void configurablePipelinesPreserveLegacyDealsAndEnforceOccupiedAndTerminalRules()
      throws Exception {
    login(email);
    UUID company = companyFixture(), deal = dealFixture(company);
    var config = Map.of("name", "Pipeline fixture", "stages", stageFixture());
    var created = request("POST", "/pipeline-definitions", config, organization, true);
    assertEquals(201, created.statusCode(), created.body());
    String pipeline = json(created).path("id").asText();
    assertEquals(
        1, json(request("GET", "/pipeline-definitions", null, organization, false)).size());
    assertTrue(
        json(request("GET", "/opportunities/" + deal, null, organization, false))
            .path("pipelineId")
            .isNull());
    var move =
        request(
            "PATCH",
            "/opportunities/" + deal + "/pipeline-stage",
            Map.of("pipelineId", pipeline, "stageKey", "discovery"),
            organization,
            true);
    assertEquals(200, move.statusCode(), move.body());
    assertEquals("discovery", json(move).path("pipelineStageKey").asText());
    assertEquals(
        200,
        request(
                "PATCH",
                "/opportunities/" + deal + "/pipeline-stage",
                Map.of("pipelineId", pipeline, "stageKey", "proposal"),
                organization,
                true)
            .statusCode());
    var removed = List.of(stageFixture().get(0), stageFixture().get(2), stageFixture().get(3));
    assertEquals(
        400,
        request(
                "PUT",
                "/pipeline-definitions/" + pipeline,
                Map.of("name", "Pipeline fixture", "stages", removed),
                organization,
                true)
            .statusCode());
    assertEquals(
        400,
        request("DELETE", "/pipeline-definitions/" + pipeline, null, organization, true)
            .statusCode());
    assertEquals(
        400,
        request(
                "PATCH",
                "/opportunities/" + deal + "/pipeline-stage",
                Map.of("pipelineId", pipeline, "stageKey", "discovery"),
                organization,
                true)
            .statusCode());
    assertEquals(
        200,
        request(
                "PATCH",
                "/opportunities/" + deal + "/pipeline-stage",
                Map.of("pipelineId", pipeline, "stageKey", "won"),
                organization,
                true)
            .statusCode());
    assertEquals(
        400,
        request(
                "PATCH",
                "/opportunities/" + deal + "/pipeline-stage",
                Map.of("pipelineId", pipeline, "stageKey", "proposal"),
                organization,
                true)
            .statusCode());
    assertEquals(
        1,
        json(request(
                "GET", "/records/opportunities?pipelineId=" + pipeline, null, organization, false))
            .path("totalElements")
            .asInt());
    assertEquals(0, json(request("GET", "/pipeline-definitions", null, second, false)).size());
    assertEquals(403, request("POST", "/pipeline-definitions", config, second, true).statusCode());
    login(viewerEmail);
    assertEquals(
        403, request("POST", "/pipeline-definitions", config, organization, true).statusCode());
    assertEquals(
        403,
        request(
                "PATCH",
                "/opportunities/" + deal + "/pipeline-stage",
                Map.of("pipelineId", pipeline, "stageKey", "won"),
                organization,
                true)
            .statusCode());
    login(email);
    assertEquals(
        204, request("DELETE", "/opportunities/" + deal, null, organization, true).statusCode());
    assertEquals(
        204,
        request("DELETE", "/pipeline-definitions/" + pipeline, null, organization, true)
            .statusCode());
  }

  /**
   * Temporary handshake for the real mounted browser; no fixture credentials enter source
   * control/logs.
   */
  @Test
  @EnabledIfEnvironmentVariable(named = "WAUTECH_BROWSER_SMOKE", matches = "true")
  void browserAcceptanceBridge() throws Exception {
    UUID viewerOrganization = UUID.randomUUID();
    jdbc.update(
        "insert into crm.organization(id,name,created_at,updated_at,archived)"
            + " values(?,?,now(),now(),false)",
        viewerOrganization,
        "Disposable viewer tenant");
    member(user, viewerOrganization, "ACTIVE", "VIEWER");
    var directory = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
    var file = directory.resolve("wautech-phase2-browser-fixture.json");
    var done = directory.resolve("wautech-phase2-browser-fixture.done");
    java.nio.file.Files.deleteIfExists(done);
    java.nio.file.Files.writeString(
        directory.resolve("wautech-phase2-import.csv"),
        "name,website,industry,phone,email,status\nBrowser imported company,,,,,ACTIVE\n");
    java.nio.file.Files.writeString(
        file,
        mapper.writeValueAsString(
            Map.of(
                "port",
                port,
                "email",
                email,
                "password",
                password,
                "organizationId",
                organization.toString(),
                "viewerOrganizationId",
                viewerOrganization.toString())));
    try {
      long deadline = System.nanoTime() + java.time.Duration.ofMinutes(20).toNanos();
      while (!java.nio.file.Files.exists(done) && System.nanoTime() < deadline) Thread.sleep(1000);
      assertTrue(
          java.nio.file.Files.exists(done),
          "Real browser acceptance did not finish before deadline");
      assertEquals("passed", java.nio.file.Files.readString(done).trim());
    } finally {
      java.nio.file.Files.deleteIfExists(file);
      java.nio.file.Files.deleteIfExists(done);
      java.nio.file.Files.deleteIfExists(directory.resolve("wautech-phase2-import.csv"));
    }
  }
}
