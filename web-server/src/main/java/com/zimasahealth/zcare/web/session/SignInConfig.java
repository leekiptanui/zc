package com.zimasahealth.zcare.web.session;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimasahealth.zcare.web.config.WebServerProperties;

/**
 * Chooses the sign-in. Fails closed: Keycloak sign-in is not built yet, and local sign-in starts
 * only under the {@code local} profile with a full-length secret and a password.
 */
@Configuration(proxyBeanMethods = false)
public class SignInConfig {

    static final int MIN_HMAC_SECRET_BYTES = 32;
    static final int MIN_LOCAL_PASSWORD_LENGTH = 12;

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LocalSignIn localSignIn(WebServerProperties properties, Environment environment, ObjectMapper json, Clock clock) {
        check(properties, environment);
        return new LocalSignIn(properties.local(), json, clock);
    }

    static void check(WebServerProperties properties, Environment environment) {
        if (properties.signIn() == WebServerProperties.SignInMode.KEYCLOAK) {
            throw new IllegalStateException("Keycloak sign-in is a placeholder and is not built yet (ADR-0007)."
                    + " For development, run with SPRING_PROFILES_ACTIVE=local and ZCARE_WEB_SIGN_IN=local.");
        }
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException("Local sign-in is for development only and needs the local profile");
        }
        WebServerProperties.Local local = properties.local();
        if (!StringUtils.hasText(local.hmacSecret())
                || local.hmacSecret().getBytes(StandardCharsets.UTF_8).length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalStateException("ZCARE_JWT_HMAC_SECRET must be set to the API's secret, at least "
                    + MIN_HMAC_SECRET_BYTES + " bytes");
        }
        if (local.password() == null || local.password().length() < MIN_LOCAL_PASSWORD_LENGTH) {
            throw new IllegalStateException("ZCARE_WEB_LOCAL_PASSWORD must be at least "
                    + MIN_LOCAL_PASSWORD_LENGTH + " characters");
        }
        if (local.users().isEmpty()) {
            throw new IllegalStateException("No local users configured (zcare.web.local.users)");
        }
    }
}
