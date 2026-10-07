package com.zimasahealth.zcare.web.session;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimasahealth.zcare.web.config.WebServerProperties;

/**
 * Development sign-in: checks a configured username and the shared local password, and signs API
 * tokens with the API's local HS256 secret (the same claims as {@code scripts/dev-token.ps1}).
 * Only created under the {@code local} profile; see {@link SignInConfig}.
 */
public class LocalSignIn implements ApiTokenSource {

    private static final String HEADER = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
            .getBytes(StandardCharsets.UTF_8));

    private final WebServerProperties.Local settings;
    private final byte[] password;
    private final SecretKeySpec key;
    private final ObjectMapper json;
    private final Clock clock;

    public LocalSignIn(WebServerProperties.Local settings, ObjectMapper json, Clock clock) {
        this.settings = settings;
        this.password = settings.password().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(settings.hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.json = json;
        this.clock = clock;
    }

    /** The user, if the username is configured and the password matches; otherwise empty. */
    public Optional<SessionUser> authenticate(String username, String candidatePassword) {
        boolean passwordMatches = candidatePassword != null && MessageDigest.isEqual(password,
                candidatePassword.getBytes(StandardCharsets.UTF_8));
        WebServerProperties.LocalUser user = username == null ? null : settings.users().get(username);
        if (user == null || !passwordMatches) {
            return Optional.empty();
        }
        return Optional.of(new SessionUser(username, user.name(), user.tenant(), user.organisation(), user.roles()));
    }

    @Override
    public String tokenFor(SessionUser user) {
        Instant now = clock.instant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.subject());
        claims.put("aud", settings.audience());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(settings.tokenLifetime()).getEpochSecond());
        claims.put(settings.tenantClaim(), user.tenant());
        claims.put("realm_access", Map.of("roles", user.roles()));
        if (user.organisation() != null) {
            claims.put("org", user.organisation());
        }
        try {
            String payload = base64Url(json.writeValueAsBytes(claims));
            String signingInput = HEADER + "." + payload;
            return signingInput + "." + base64Url(sign(signingInput));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write token claims", e);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
