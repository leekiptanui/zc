package com.zimasahealth.zcare.security;

import java.io.IOException;
import java.util.Optional;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.context.Actor;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.tenant.CurrentTenant;
import com.zimasahealth.zcare.tenant.TenantContext;
import com.zimasahealth.zcare.tenant.TenantRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds an authenticated request to its tenant, taken only from the verified token's tenant
 * claim (ADR-0005). The tenant is never read from the URL or the request body, so a client cannot
 * name it and it never appears in an address.
 *
 * <p>A request that sends {@code X-Tenant-Id} naming a different tenant is refused with 403 and
 * a {@code tenant_violation_attempt} security event. A token naming no tenant, or an unknown,
 * suspended or closed one, is refused with 403. The tenant is held for the request and cleared in
 * {@code finally} (CODE-07); the transaction manager writes it to {@code zcare.tenant_id} at every
 * transaction start.
 */
public class TenantBindingFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantRegistry tenants;
    private final SecurityEventWriter events;

    public TenantBindingFilter(TenantRegistry tenants, SecurityEventWriter events) {
        this.tenants = tenants;
        this.events = events;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ApiPaths.BASE + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Actor> actor = CurrentActor.current();
        if (actor.isEmpty()) {
            chain.doFilter(request, response); // no valid token: authorisation answers 401
            return;
        }
        String tokenTenant = actor.get().tenantCode();
        if (tokenTenant == null || tokenTenant.isBlank()) {
            events.record(SecurityEventType.AUTHORISATION_DENIED, null, actor.get().id(), request,
                    "token carries no tenant claim");
            SecurityResponses.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden");
            return;
        }
        Optional<CurrentTenant> tenant = tenants.findActive(tokenTenant);
        String headerTenant = request.getHeader(TENANT_HEADER);
        if (headerTenant != null && !headerTenant.equals(tokenTenant)) {
            events.record(SecurityEventType.TENANT_VIOLATION_ATTEMPT, tenant.map(CurrentTenant::id).orElse(null),
                    actor.get().id(), request, "header tenant differs from the token's tenant");
            SecurityResponses.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden");
            return;
        }
        if (tenant.isEmpty()) {
            events.record(SecurityEventType.AUTHORISATION_DENIED, null, actor.get().id(), request,
                    "token tenant is unknown or not active");
            SecurityResponses.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden");
            return;
        }

        TenantContext.set(tenant.get());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
