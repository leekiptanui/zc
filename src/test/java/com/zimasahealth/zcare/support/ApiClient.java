package com.zimasahealth.zcare.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Calls the API under test as a given caller, with HS256 tokens signed by the test secret. */
public final class ApiClient {

    public static final String HMAC_SECRET = "integration-test-secret-of-at-least-32-bytes";
    public static final String AUDIENCE = "zimasa-zcare-service";

    private final TestRestTemplate rest;
    private final ObjectMapper json;

    public ApiClient(TestRestTemplate rest, ObjectMapper json) {
        // The JDK HttpClient reads a 401 body; HttpURLConnection fails on 401 to a streamed POST.
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        this.rest = rest;
        this.json = json;
    }

    /** A signed token for the subject and role, issued for {@code tokenTenant}. */
    public static String token(String subject, String role, String tokenTenant, Long organisationId) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .audience(AUDIENCE)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("tenant", tokenTenant)
                .claim("realm_access", Map.of("roles", List.of(role)));
        if (organisationId != null) {
            claims.claim("org", organisationId);
        }
        return sign(claims.build());
    }

    public static String expiredToken(String subject, String role, String tokenTenant) {
        return sign(new JWTClaimsSet.Builder().subject(subject).audience(AUDIENCE)
                .expirationTime(Date.from(Instant.now().minusSeconds(120)))
                .claim("tenant", tokenTenant).claim("realm_access", Map.of("roles", List.of(role))).build());
    }

    private static String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(HMAC_SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public Response get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null, null);
    }

    public Response post(String path, String token, Object body) {
        return exchange(HttpMethod.POST, path, token, body, UUID.randomUUID().toString());
    }

    public Response post(String path, String token, Object body, String idempotencyKey) {
        return exchange(HttpMethod.POST, path, token, body, idempotencyKey);
    }

    /** A GET carrying one extra header, such as a spoofed {@code X-Tenant-Id}. */
    public Response getWithHeader(String path, String token, String header, String value) {
        return exchange(HttpMethod.GET, path, token, null, null, Map.of(header, value));
    }

    public Response exchange(HttpMethod method, String path, String token, Object body, String idempotencyKey) {
        return exchange(method, path, token, body, idempotencyKey, Map.of());
    }

    /** {@code path} is relative to {@code /api/v1}, unless it starts with {@code //}. */
    public Response exchange(HttpMethod method, String path, String token, Object body, String idempotencyKey,
                             Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        extraHeaders.forEach(headers::set);
        String url = path.startsWith("//") ? path.substring(1) : "/api/v1" + path;
        try {
            String payload = body == null ? (method == HttpMethod.GET ? null : "{}") : json.writeValueAsString(body);
            ResponseEntity<String> response = rest.exchange(url, method, new HttpEntity<>(payload, headers),
                    String.class);
            JsonNode node = response.getBody() == null ? json.nullNode() : json.readTree(response.getBody());
            return new Response(response.getStatusCode().value(), node, response.getHeaders());
        } catch (Exception e) {
            throw new IllegalStateException("Call failed: " + method + " " + url, e);
        }
    }

    /** One HTTP response with its parsed body. */
    public record Response(int status, JsonNode body, HttpHeaders headers) {

        public String envelopeStatus() {
            return body.path("status").asText();
        }

        public String firstCode() {
            return body.path("exceptions").path(0).path("code").asText();
        }

        public JsonNode data() {
            return body.path("data");
        }

        public long id(String field) {
            return data().path(field).asLong();
        }
    }
}
