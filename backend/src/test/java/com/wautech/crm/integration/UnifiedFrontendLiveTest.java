package com.wautech.crm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in actual HTTP/persistence tests. Exact fixture database gate prevents real account changes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "WAUTECH_TEST_DB_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:15432/wautech_integration_fixture")
class UnifiedFrontendLiveTest {
    @DynamicPropertySource static void fixtureDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("WAUTECH_TEST_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:15432/wautech_integration_fixture".equals(url)) throw new IllegalStateException("Isolated fixture database required");
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
    @BeforeEach void fixtures() throws Exception {
        user=UUID.randomUUID(); viewer=UUID.randomUUID(); organization=UUID.randomUUID(); second=UUID.randomUUID(); inactive=UUID.randomUUID(); archived=UUID.randomUUID();
        email=user+"@fixture.example.test"; viewerEmail=viewer+"@fixture.example.test"; password=UUID.randomUUID().toString();
        for (UUID id:List.of(organization,second,inactive,archived)) jdbc.update("insert into crm.organization(id,name,created_at,updated_at,archived) values(?,?,now(),now(),?)",id,"Integration fixture "+id,id.equals(archived));
        for(var entry:Map.of(user,email,viewer,viewerEmail).entrySet()) jdbc.update("insert into crm.user_account(id,email,first_name,last_name,enabled,password_hash,created_at,updated_at) values(?,?,?, ?,true,?,now(),now())",entry.getKey(),entry.getValue(),"Fixture","User",encoder.encode(password));
        member(user,organization,"ACTIVE","ADMIN"); member(user,second,"ACTIVE","SALES_USER"); member(user,inactive,"REVOKED","ADMIN"); member(user,archived,"ACTIVE","OWNER"); member(viewer,organization,"ACTIVE","VIEWER");
        cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL); client=HttpClient.newBuilder().cookieHandler(cookies).build();
    }
    void member(UUID userId,UUID org,String status,String role) { jdbc.update("insert into crm.organization_membership(id,organization_id,user_id,status,role,joined_at,created_at,updated_at) values(?,?,?,?,?,now(),now(),now())",UUID.randomUUID(),org,userId,status,role); }
    HttpResponse<String> request(String method,String path,Object body,UUID org,boolean csrf) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Accept","application/json");
        if(org!=null)builder.header("X-Organization-ID",org.toString());
        if(csrf){var token=json(request("GET","/auth/csrf",null,null,false));builder.header("X-CSRF-TOKEN",token.path("token").asText());}
        if(body!=null)builder.header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
    void login(String address) throws Exception { assertEquals(200,request("POST","/auth/login",Map.of("email",address,"password",password),null,true).statusCode()); assertEquals(200,request("GET","/auth/me",null,null,false).statusCode()); }
    @Test void loginRestorationCsrfLogoutAndDisabledSession() throws Exception {
        assertEquals(401,request("GET","/auth/me",null,null,false).statusCode());
        assertEquals(403,request("POST","/auth/login",Map.of("email",email,"password",password),null,false).statusCode());
        assertEquals(401,request("POST","/auth/login",Map.of("email",email,"password",UUID.randomUUID().toString()),null,true).statusCode());
        login(email); assertEquals(user.toString(),json(request("GET","/auth/me",null,null,false)).path("id").asText());
        assertEquals(204,request("POST","/auth/logout",null,null,true).statusCode()); assertEquals(401,request("GET","/auth/me",null,null,false).statusCode());
        login(email); jdbc.update("update crm.user_account set enabled=false where id=?",user);
        assertEquals(401,request("GET","/auth/me",null,null,false).statusCode());
    }
    @Test void discoverySelectionRevocationAndTenantIsolation() throws Exception {
        assertEquals(401,request("GET","/auth/organizations",null,null,false).statusCode()); login(email);
        var discovery=request("GET","/auth/organizations",null,null,false);assertEquals(200,discovery.statusCode());assertEquals(2,json(discovery).size());assertEquals("no-store",discovery.headers().firstValue("cache-control").orElse(""));
        assertEquals(200,request("GET","/auth/organizations/"+organization+"/membership",null,null,false).statusCode());
        assertEquals(403,request("GET","/auth/organizations/"+inactive+"/membership",null,null,false).statusCode());
        assertEquals(403,request("GET","/auth/organizations/"+archived+"/membership",null,null,false).statusCode());
        assertEquals(403,request("GET","/auth/organizations/"+UUID.randomUUID()+"/membership",null,null,false).statusCode());
        assertEquals(403,request("GET","/companies",null,null,false).statusCode());
        var company=request("POST","/companies",Map.of("name","Isolated company"),organization,true);assertEquals(201,company.statusCode());String id=json(company).path("id").asText();
        assertEquals(404,request("GET","/companies/"+id,null,second,false).statusCode());
        assertEquals(200,request("GET","/companies/"+id,null,organization,false).statusCode());
        jdbc.update("update crm.organization_membership set status='REVOKED' where organization_id=? and user_id=?",organization,user);
        assertEquals(403,request("GET","/companies",null,organization,false).statusCode());assertEquals(200,request("GET","/auth/me",null,null,false).statusCode());
    }
    @Test void viewerCanReadButCannotWrite() throws Exception {
        login(viewerEmail); assertEquals("VIEWER",json(request("GET","/auth/organizations",null,null,false)).get(0).path("role").asText());
        assertEquals(200,request("GET","/companies",null,organization,false).statusCode());
        assertEquals(403,request("POST","/companies",Map.of("name","Denied fixture"),organization,true).statusCode());
        assertEquals(200,request("GET","/auth/me",null,null,false).statusCode());
    }
    @Test void realCrmCrudTransitionsAndDashboardContracts() throws Exception {
        login(email);
        var company=create("companies",Map.of("name","Fixture company"));
        String companyId=company.path("id").asText();
        var contactBody=Map.of("companyId",companyId,"firstName","Fixture","lastName","Contact");
        var contact=create("contacts",contactBody); String contactId=contact.path("id").asText();
        var leadBody=Map.of("companyId",companyId,"firstName","Fixture","lastName","Lead");
        var lead=create("leads",leadBody);String leadId=lead.path("id").asText();
        var opportunityBody=Map.of("companyId",companyId,"contactId",contactId,"name","Fixture deal","amount",125.50,"currency","USD");
        var opportunity=create("opportunities",opportunityBody);String opportunityId=opportunity.path("id").asText();
        for(var entry:Map.of("companies",Map.of("name","Updated fixture"),"contacts",contactBody,"leads",leadBody,"opportunities",opportunityBody).entrySet()) {
            String id=Map.of("companies",companyId,"contacts",contactId,"leads",leadId,"opportunities",opportunityId).get(entry.getKey());
            assertEquals(200,request("GET","/"+entry.getKey()+"/"+id,null,organization,false).statusCode());
            assertEquals(200,request("PUT","/"+entry.getKey()+"/"+id,entry.getValue(),organization,true).statusCode());
            assertTrue(json(request("GET","/"+entry.getKey()+"?search=Fixture&sortBy=createdAt&sortDirection=desc",null,organization,false)).isArray());
        }
        assertEquals(200,request("PATCH","/leads/"+leadId+"/status",Map.of("status","CONTACTED"),organization,true).statusCode());
        assertEquals(200,request("PATCH","/opportunities/"+opportunityId+"/stage",Map.of("stage","NEEDS_ANALYSIS"),organization,true).statusCode());
        assertEquals(200,request("GET","/pipeline",null,organization,false).statusCode());
        var tasks=request("GET","/tasks",null,organization,false); assertEquals(200,tasks.statusCode(),tasks.body()); assertTrue(json(tasks).isArray()); var activities=request("GET","/activities",null,organization,false); assertEquals(200,activities.statusCode(),activities.body()); assertTrue(json(activities).isArray());
        var inbox=request("GET","/notifications",null,organization,false); assertEquals(200,inbox.statusCode(),inbox.body()); assertTrue(json(inbox).has("content"),inbox.body()); var unread=request("GET","/notifications/unread-count",null,organization,false); assertEquals(200,unread.statusCode(),unread.body()); assertTrue(json(unread).has("count"),unread.body());
        for(var entry:List.of(Map.entry("opportunities",opportunityId),Map.entry("leads",leadId),Map.entry("contacts",contactId),Map.entry("companies",companyId))) {
            assertEquals(204,request("DELETE","/"+entry.getKey()+"/"+entry.getValue(),null,organization,true).statusCode());
            assertEquals(404,request("GET","/"+entry.getKey()+"/"+entry.getValue(),null,organization,false).statusCode());
        }
    }
    JsonNode create(String resource,Object body) throws Exception {var response=request("POST","/"+resource,body,organization,true);assertEquals(201,response.statusCode(),response.body());return json(response);}
}



