package com.zimasahealth.zcare.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import com.zimasahealth.zcare.common.context.RequestCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Takes {@code X-Correlation-Id} from the caller, or generates one, and carries it in the MDC,
 * the response header, the envelope, audit rows and events (04B section 9). Runs before
 * security so that even a 401 can be traced.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";

    // A caller-supplied id is echoed into logs and responses, so only a safe shape is accepted.
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = supplied != null && SAFE.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        RequestCorrelation.set(UUID.randomUUID().toString(), correlationId);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            RequestCorrelation.clear();
        }
    }
}
