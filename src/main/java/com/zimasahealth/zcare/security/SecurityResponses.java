package com.zimasahealth.zcare.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.zimasahealth.zcare.common.context.RequestCorrelation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

/**
 * Bodies of 401 and 403 responses. These sit outside the envelope by design: authentication and
 * authorisation are transport outcomes, not business exceptions (04B section 6).
 */
final class SecurityResponses {

    private SecurityResponses() {
    }

    static Map<String, String> body(String error) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("correlationId", RequestCorrelation.correlationId());
        return body;
    }

    static void write(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\",\"correlationId\":\""
                + RequestCorrelation.correlationId() + "\"}");
    }
}
