package com.zimasahealth.zcare.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/**
 * The browser contract of ADR-0007: the browser holds only a session cookie and an anti-CSRF
 * cookie; the token and the tenant exist only between this server and the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WebServerSessionTest {

    static final String SECRET = "test-secret-test-secret-test-secret-0123";
    static final String PASSWORD = "local-password-123";
    static final StubApi API = start();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    Cookie xsrf;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("zcare.web.sign-in", () -> "local");
        registry.add("zcare.web.api-base-url", API::baseUrl);
        registry.add("zcare.web.local.hmac-secret", () -> SECRET);
        registry.add("zcare.web.local.password", () -> PASSWORD);
    }

    @AfterAll
    static void stop() {
        API.close();
    }

    @BeforeEach
    void freshCsrfToken() throws Exception {
        API.reset();
        xsrf = mvc.perform(get("/bff/user")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
    }

    @Test
    void signedOutCallsAreRefusedWithoutReachingTheApi() throws Exception {
        mvc.perform(get("/bff/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/v1/programmes"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
        assertThat(API.last()).isNull();
    }

    @Test
    void signInNeedsTheAntiCsrfHeaderAndTheRightPassword() throws Exception {
        mvc.perform(post("/bff/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cm-001\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/bff/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cm-001\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(withCsrf(post("/bff/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theBrowserLearnsOnlyTheNameAndRoles() throws Exception {
        MockHttpSession session = signIn("cm-001");
        MockHttpServletResponse user = mvc.perform(get("/bff/user").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value("Care Manager (local)"))
                .andExpect(jsonPath("$.roles[0]").value("care_manager"))
                .andReturn().getResponse();
        assertThat(user.getContentAsString()).doesNotContain("acme-health", "cm-001", "token");
    }

    @Test
    void apiCallsCarryTheTokenUpstreamAndNothingElse() throws Exception {
        MockHttpSession session = signIn("cm-001");
        MockHttpServletResponse response = mvc.perform(get("/api/v1/programmes?page=1&pageSize=20")
                        .session(session)
                        .cookie(xsrf, new Cookie("zcare", "session-id"))
                        .header("X-Tenant-Id", "other-tenant")
                        .header("X-Correlation-Id", "corr-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        StubApi.Received received = API.last();
        assertThat(received.method()).isEqualTo("GET");
        assertThat(received.pathAndQuery()).isEqualTo("/api/v1/programmes?page=1&pageSize=20");
        assertThat(received.headers().getFirst("X-Correlation-Id")).isEqualTo("corr-1");
        assertThat(received.headers()).doesNotContainKeys("Cookie", "X-tenant-id", "X-xsrf-token");

        String authorization = received.headers().getFirst("Authorization");
        assertThat(authorization).startsWith("Bearer ");
        Map<?, ?> claims = claims(authorization.substring("Bearer ".length()));
        assertThat(claims.get("sub")).isEqualTo("cm-001");
        assertThat(claims.get("tenant")).isEqualTo("acme-health");
        assertThat(claims.get("aud")).isEqualTo("zimasa-zcare-service");
        assertThat(((Map<?, ?>) claims.get("realm_access")).get("roles")).asList().containsExactly("care_manager");

        assertThat(response.getContentAsString()).isEqualTo(StubApi.ENVELOPE);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("corr-1");
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(cookie -> cookie.contains("upstream"));
        assertThat(response.getHeader("X-Internal-Tenant")).isNull();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
    }

    @Test
    void statefulCallsNeedTheAntiCsrfHeader() throws Exception {
        MockHttpSession session = signIn("pa-001");
        mvc.perform(post("/api/v1/programmes").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DM\"}"))
                .andExpect(status().isForbidden());
        assertThat(API.last()).isNull();

        mvc.perform(withCsrf(post("/api/v1/programmes")).session(session).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "6f1c1f1e-2a8d-4a57-9a39-2c1f1f1e2a8d")
                        .content("{\"code\":\"DM\"}"))
                .andExpect(status().isOk());
        StubApi.Received received = API.last();
        assertThat(received.method()).isEqualTo("POST");
        assertThat(received.body()).isEqualTo("{\"code\":\"DM\"}");
        assertThat(received.headers().getFirst("Idempotency-Key")).isEqualTo("6f1c1f1e-2a8d-4a57-9a39-2c1f1f1e2a8d");
        assertThat(received.headers().getFirst("Content-Type")).startsWith("application/json");
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        MockHttpSession session = signIn("cm-001");
        mvc.perform(withCsrf(post("/bff/logout")).session(session))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/programmes").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
        assertThat(API.last()).isNull();
    }

    @Test
    void signInReplacesAnyEarlierSession() throws Exception {
        MockHttpSession before = new MockHttpSession();
        MockHttpSession after = (MockHttpSession) mvc.perform(withCsrf(post("/bff/login")).session(before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cm-001\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        assertThat(before.isInvalid()).isTrue();
        assertThat(after).isNotSameAs(before);
    }

    @Test
    void onlyApiV1IsForwarded() throws Exception {
        MockHttpSession session = signIn("plat-001");
        mvc.perform(get("/api/v2/programmes").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/env").session(session)).andExpect(status().isNotFound());
        assertThat(API.last()).isNull();
    }

    @Test
    void theAppIsServedHardenedAndWithoutSourceMaps() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mvc.perform(get("/check.js.map")).andExpect(status().isNotFound());
    }

    private MockHttpSession signIn(String username) throws Exception {
        return (MockHttpSession) mvc.perform(withCsrf(post("/bff/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    private Map<?, ?> claims(String token) throws Exception {
        String payload = token.split("\\.")[1];
        return json.readValue(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8), Map.class);
    }

    private static StubApi start() {
        try {
            return new StubApi();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
