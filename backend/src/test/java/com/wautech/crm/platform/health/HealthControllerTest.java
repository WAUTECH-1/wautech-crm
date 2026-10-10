package com.wautech.crm.platform.health;

import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.security.SecurityConfiguration;
import com.wautech.crm.platform.health.DatabaseReadiness;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import(SecurityConfiguration.class)
class HealthControllerTest extends SecurityMvcTestSupport {
    @MockitoBean private DatabaseReadiness databaseReadiness;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsRunningStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void livenessAndReadinessRoutesRemainPublic() throws Exception {
        mockMvc.perform(get("/api/health/liveness")).andExpect(status().isOk());
        when(databaseReadiness.isDatabaseReady()).thenReturn(true);
        mockMvc.perform(get("/api/health/readiness")).andExpect(status().isOk());
        // The actuator handler is not loaded in this MVC slice; reaching 404 proves security permits the route.
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
    }

    @Test
    void readinessIsMinimalAndReportsDatabaseUnavailableWithoutDetails() throws Exception {
        when(databaseReadiness.isDatabaseReady()).thenReturn(false);

        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}"))
                .andExpect(jsonPath("$.database").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void malformedCorrelationIdIsReplacedAndValidIdsAreReturned() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Correlation-ID", "this-is-invalid"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(header().string("X-Correlation-ID", org.hamcrest.Matchers.matchesRegex(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));

        String id = "c6ea7656-a96c-46bb-966e-01dd3f15e9ad";
        mockMvc.perform(get("/api/health").header("X-Correlation-ID", id.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", id));
    }
}
