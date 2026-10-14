package com.thepourover.cdp.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitytest;DB_CLOSE_DELAY=-1",
        "spring.ai.openai.api-key=test-key",
        "spring.h2.console.enabled=false",
        "cdp.webhook-secret=test-webhook-secret",
        "cdp.pii-hash-salt=test-salt",
        "cdp.seed-on-startup=false",
        "cdp.users[0].username=growth",
        "cdp.users[0].password=pour-over",
        "cdp.github-allowed-logins="
})
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void h2ConsoleDeniedWhenDisabled() throws Exception {
        mockMvc.perform(get("/h2-console").with(user("growth")))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerAllowedWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("growth")))
                .andExpect(status().isOk());
    }

    @Test
    void mutatingApiRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(user("growth")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWorksWithCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"growth\",\"password\":\"pour-over\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void webhookRejectsMissingBearer() throws Exception {
        mockMvc.perform(post("/webhooks/app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event_id":"e1","event":"app_open","device_id":"d1",
                                 "timestamp":"2026-09-28T10:00:00Z","properties":{}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookAcceptsBearerWithoutCsrf() throws Exception {
        mockMvc.perform(post("/webhooks/app")
                        .header("Authorization", "Bearer test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event_id":"e_sec_1","event":"app_open","user_id":null,"device_id":"d_sec",
                                 "timestamp":"2026-09-28T10:00:00Z","properties":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
