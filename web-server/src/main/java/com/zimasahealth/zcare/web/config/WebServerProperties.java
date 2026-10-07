package com.zimasahealth.zcare.web.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Web server settings. Every environment-specific value comes from an environment variable; see
 * {@code .env.example}.
 *
 * @param apiBaseUrl            the ZCare API, reachable from this server only; never from a browser
 * @param signIn                how users sign in; {@code keycloak} is a placeholder that refuses to start
 * @param secureCookies         mark the session and anti-CSRF cookies {@code Secure}; false only for local HTTP
 * @param maxRequestBytes       largest request body forwarded to the API
 * @param contentSecurityPolicy sent on every response
 * @param local                 local sign-in, for development only
 */
@ConfigurationProperties("zcare.web")
public record WebServerProperties(
        @DefaultValue("http://localhost:8080") URI apiBaseUrl,
        @DefaultValue("keycloak") SignInMode signIn,
        @DefaultValue("true") boolean secureCookies,
        @DefaultValue("1048576") int maxRequestBytes,
        @DefaultValue(DEFAULT_CSP) String contentSecurityPolicy,
        @DefaultValue Local local) {

    public static final String DEFAULT_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
            + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    public enum SignInMode {
        /** Development only: users and the token-signing secret are configured on this server. */
        LOCAL,
        /** Placeholder until the Keycloak realm and clients are agreed (OD-11). */
        KEYCLOAK
    }

    /**
     * Local sign-in. The server signs API tokens itself with the API's local HS256 secret, so the
     * flow matches production: the browser holds only the session cookie.
     *
     * @param hmacSecret    the API's {@code ZCARE_JWT_HMAC_SECRET}
     * @param password      shared password for every local user
     * @param audience      {@code aud} the API requires
     * @param tenantClaim   claim the API reads the tenant from
     * @param tokenLifetime lifetime of each minted token; a fresh one is minted per call
     * @param users         local users by username
     */
    public record Local(
            String hmacSecret,
            String password,
            @DefaultValue("zimasa-zcare-service") String audience,
            @DefaultValue("tenant") String tenantClaim,
            @DefaultValue("PT2M") Duration tokenLifetime,
            Map<String, LocalUser> users) {

        public Map<String, LocalUser> users() {
            return users == null ? Map.of() : users;
        }
    }

    /**
     * @param name         shown in the web app
     * @param tenant       tenant code; held server-side and never sent to the browser
     * @param roles        ZCare role codes
     * @param organisation a provider user's {@code zc_organisation.id}, or null
     */
    public record LocalUser(String name, String tenant, List<String> roles, Long organisation) {
    }
}
