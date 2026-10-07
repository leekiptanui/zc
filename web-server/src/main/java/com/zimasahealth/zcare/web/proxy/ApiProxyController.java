package com.zimasahealth.zcare.web.proxy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zimasahealth.zcare.web.config.WebServerProperties;
import com.zimasahealth.zcare.web.session.ApiTokenSource;
import com.zimasahealth.zcare.web.session.SessionUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Forwards the web app's {@code /api/v1/...} calls to the ZCare API with the session's bearer
 * token. Headers are allow-listed both ways: cookies, the anti-CSRF token and any tenant header
 * never reach the API, and nothing from the API but the content type, correlation id and replay
 * flag reaches the browser.
 */
@RestController
public class ApiProxyController {

    static final String PREFIX = "/api/v1/";

    private static final Logger log = LoggerFactory.getLogger(ApiProxyController.class);
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final List<String> REQUEST_HEADERS =
            List.of("Accept", "Accept-Language", "Content-Type", "Idempotency-Key", "X-Correlation-Id");
    private static final List<String> RESPONSE_HEADERS =
            List.of("Content-Type", "Idempotency-Replay", "X-Correlation-Id");

    private final WebServerProperties properties;
    private final ApiTokenSource tokens;
    private final String apiBase;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ApiProxyController(WebServerProperties properties, ApiTokenSource tokens) {
        this.properties = properties;
        this.tokens = tokens;
        this.apiBase = properties.apiBaseUrl().toString().replaceAll("/+$", "");
    }

    @RequestMapping(PREFIX + "**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request, @AuthenticationPrincipal SessionUser user)
            throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (user == null || !path.startsWith(PREFIX) || path.contains("..") || !METHODS.contains(method)) {
            return ResponseEntity.notFound().build();
        }

        byte[] body = request.getInputStream().readNBytes(properties.maxRequestBytes() + 1);
        if (body.length > properties.maxRequestBytes()) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        String query = request.getQueryString();
        HttpRequest.Builder upstream = HttpRequest.newBuilder(URI.create(apiBase + path + (query == null ? "" : "?" + query)))
                .timeout(Duration.ofSeconds(30))
                .method(method, body.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(user));
        for (String name : REQUEST_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
                upstream.header(name, value);
            }
        }

        HttpResponse<byte[]> answer;
        try {
            answer = client.send(upstream.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (IOException e) {
            log.warn("ZCare API unreachable: {} {}", method, e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        HttpHeaders headers = new HttpHeaders();
        for (String name : RESPONSE_HEADERS) {
            answer.headers().firstValue(name).ifPresent(value -> headers.set(name, value));
        }
        return ResponseEntity.status(answer.statusCode()).headers(headers).body(answer.body());
    }
}
