package com.zimasahealth.zcare.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token validation and claim names. Keycloak realm, clients and claims are still being agreed
 * with the platform team (OD-11), so every claim name is configurable.
 *
 * @param jwt    how tokens are verified
 * @param claims where the tenant and organisation are read from
 */
@ConfigurationProperties("zcare.security")
public record ZcareSecurityProperties(@DefaultValue Jwt jwt, @DefaultValue Claims claims) {

    /**
     * @param issuerUri  Keycloak realm issuer; signing keys are discovered from it and {@code iss} is checked
     * @param jwkSetUri  signing keys, when they are not discovered from the issuer
     * @param audience   required {@code aud} value; blank skips the check
     * @param hmacSecret shared HS256 secret for local runs and tests only; never set in a deployed environment
     */
    public record Jwt(String issuerUri, String jwkSetUri,
                      @DefaultValue("zimasa-zcare-service") String audience, String hmacSecret) {
    }

    /**
     * @param tenant       claim holding the tenant code, matched against the URL
     * @param organisation claim holding a provider user's {@code zc_organisation.id}
     */
    public record Claims(@DefaultValue("tenant") String tenant, @DefaultValue("org") String organisation) {
    }
}
