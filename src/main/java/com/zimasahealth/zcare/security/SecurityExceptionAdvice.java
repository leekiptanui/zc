package com.zimasahealth.zcare.security;

import java.util.Map;

import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.tenant.CurrentTenant;
import com.zimasahealth.zcare.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 403s raised inside Spring MVC, outside the envelope: a role refused by {@code @PreAuthorize},
 * and any route or method the API does not register (fail closed, M02-15). Each is written to
 * {@code zc_security_event}. Ordered before the global handler so these never become envelopes.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    private final SecurityEventWriter events;

    public SecurityExceptionAdvice(SecurityEventWriter events) {
        this.events = events;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        record(request, "role not permitted: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SecurityResponses.body("forbidden"));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Map<String, String>> unregisteredRoute(Exception ex, HttpServletRequest request) {
        record(request, "unregistered route");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SecurityResponses.body("forbidden"));
    }

    private void record(HttpServletRequest request, String detail) {
        events.record(SecurityEventType.AUTHORISATION_DENIED,
                TenantContext.current().map(CurrentTenant::id).orElse(null),
                CurrentActor.current().map(a -> a.id()).orElse(null), request, detail);
    }
}
