package com.zimasahealth.zcare.web.security;

import java.io.IOException;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Never serves source maps, even if a build ships them by mistake: they would hand the web app's
 * full source to anyone with dev tools (ADR-0007).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SourceMapFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().toLowerCase(Locale.ROOT).endsWith(".map")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
