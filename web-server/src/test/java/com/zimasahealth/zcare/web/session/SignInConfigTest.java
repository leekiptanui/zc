package com.zimasahealth.zcare.web.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimasahealth.zcare.web.config.WebServerProperties;
import com.zimasahealth.zcare.web.config.WebServerProperties.LocalUser;
import com.zimasahealth.zcare.web.config.WebServerProperties.SignInMode;

class SignInConfigTest {

    static final String SECRET = "test-secret-test-secret-test-secret-0123";
    static final String PASSWORD = "local-password-123";

    @Test
    void keycloakIsAPlaceholderThatRefusesToStart() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SignInConfig.check(properties(SignInMode.KEYCLOAK, SECRET, PASSWORD), local()))
                .withMessageContaining("placeholder");
    }

    @Test
    void localSignInNeedsTheLocalProfile() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SignInConfig.check(properties(SignInMode.LOCAL, SECRET, PASSWORD), new MockEnvironment()))
                .withMessageContaining("local profile");
    }

    @Test
    void localSignInNeedsAFullLengthSecretAndAPassword() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SignInConfig.check(properties(SignInMode.LOCAL, "short", PASSWORD), local()));
        assertThatIllegalStateException()
                .isThrownBy(() -> SignInConfig.check(properties(SignInMode.LOCAL, SECRET, "short"), local()));
        SignInConfig.check(properties(SignInMode.LOCAL, SECRET, PASSWORD), local());
    }

    @Test
    void mintsTokensTheApiAccepts() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
        LocalSignIn signIn = new LocalSignIn(properties(SignInMode.LOCAL, SECRET, PASSWORD).local(), new ObjectMapper(), clock);
        SessionUser user = signIn.authenticate("pc-001", PASSWORD).orElseThrow();

        String[] parts = signIn.tokenFor(user).split("\\.");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII)));
        assertThat(parts[2]).isEqualTo(expected);

        Map<?, ?> claims = new ObjectMapper().readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
        assertThat(claims.get("sub")).isEqualTo("pc-001");
        assertThat(claims.get("tenant")).isEqualTo("acme-health");
        assertThat(claims.get("org")).isEqualTo(3);
        assertThat(((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue()).isEqualTo(120);
        assertThat(user.toString()).doesNotContain("acme-health");
    }

    private static MockEnvironment local() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        return environment;
    }

    private static WebServerProperties properties(SignInMode mode, String secret, String password) {
        return new WebServerProperties(URI.create("http://localhost:8080"), mode, false, 1024,
                WebServerProperties.DEFAULT_CSP,
                new WebServerProperties.Local(secret, password, "zimasa-zcare-service", "tenant", Duration.ofMinutes(2),
                        Map.of("pc-001", new LocalUser("Provider Coordinator", "acme-health",
                                List.of("provider_coordinator"), 3L))));
    }
}
