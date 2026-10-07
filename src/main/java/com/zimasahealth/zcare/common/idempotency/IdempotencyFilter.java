package com.zimasahealth.zcare.common.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimasahealth.zcare.common.api.ApiException;
import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.EnvelopeStatus;
import com.zimasahealth.zcare.common.context.Actor;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotency for every mutating call, implemented once and never per endpoint (04B section 7;
 * ENG-STD-SB-001 section 2.12):
 * <ul>
 *   <li>no {@code Idempotency-Key}: VALIDATION_FIELD_REQUIRED envelope;</li>
 *   <li>first call: executed, and its envelope, success or business exception, cached 24 hours;</li>
 *   <li>same key and body again: the cached envelope, unchanged, with {@code Idempotency-Replay: true};</li>
 *   <li>same key, different body: IDEMPOTENCY_KEY_CONFLICT (HARD_STOP).</li>
 * </ul>
 * Runs after authentication and tenant resolution, so the cache is per tenant, actor and route.
 * Non-200 responses (401, 403, 500) are never cached: a retry re-executes.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAY_HEADER = "Idempotency-Replay";
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern API_PATH = Pattern.compile("^" + Pattern.quote(ApiPaths.BASE) + "/.+");

    private final IdempotencyStore store;
    private final ObjectMapper json;

    public IdempotencyFilter(IdempotencyStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MUTATING.contains(request.getMethod()) || !API_PATH.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Actor> actor = CurrentActor.current();
        if (actor.isEmpty() || TenantContext.current().isEmpty()) {
            chain.doFilter(request, response); // unauthenticated: security answers with 401 or 403
            return;
        }
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            writeEnvelope(response, ZCareExceptionCode.VALIDATION_FIELD_REQUIRED,
                    "The Idempotency-Key header is required on mutating calls", HEADER);
            return;
        }
        if (!isUuid(key)) {
            writeEnvelope(response, ZCareExceptionCode.VALIDATION_FIELD_INVALID,
                    "Idempotency-Key must be a UUID", HEADER);
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String bodyHash = sha256(body);
        String route = request.getRequestURI();
        String actorId = actor.get().id();

        Optional<IdempotencyStore.CachedResponse> cached = store.find(key, actorId, route);
        if (cached.isPresent()) {
            if (cached.get().bodyHash().equals(bodyHash)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader(REPLAY_HEADER, "true");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getOutputStream().write(cached.get().envelopeJson().getBytes(StandardCharsets.UTF_8));
            } else {
                writeEnvelope(response, ZCareExceptionCode.IDEMPOTENCY_KEY_CONFLICT,
                        "This Idempotency-Key was already used with a different request body", null);
            }
            return;
        }

        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(new CachedBodyRequest(request, body), captured);
            byte[] content = captured.getContentAsByteArray();
            if (captured.getStatus() == HttpServletResponse.SC_OK && isEnvelope(content)) {
                store.save(key, actorId, route, bodyHash, new String(content, StandardCharsets.UTF_8));
            }
        } finally {
            captured.copyBodyToResponse();
        }
    }

    private boolean isEnvelope(byte[] content) {
        if (content.length == 0) {
            return false;
        }
        try {
            JsonNode node = json.readTree(content);
            return node.isObject() && node.hasNonNull("requestId") && node.hasNonNull("status");
        } catch (IOException e) {
            return false;
        }
    }

    private void writeEnvelope(HttpServletResponse response, ZCareExceptionCode code, String message, String field)
            throws IOException {
        ApiResponse<Object> envelope = ApiResponse.of(EnvelopeStatus.EXCEPTION, null)
                .addException(ApiException.of(code, message, field, Map.of()))
                .nextActions(code.nextActions());
        envelope.complete("IDEMPOTENCY");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), envelope);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return value.length() == 36;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
