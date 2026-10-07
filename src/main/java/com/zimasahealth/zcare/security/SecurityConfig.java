package com.zimasahealth.zcare.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.idempotency.IdempotencyFilter;
import com.zimasahealth.zcare.common.idempotency.IdempotencyStore;
import com.zimasahealth.zcare.tenant.CurrentTenant;
import com.zimasahealth.zcare.tenant.TenantContext;
import com.zimasahealth.zcare.tenant.TenantRegistry;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * The filter chain (M02-15): stateless bearer-token authentication against Keycloak, the tenant
 * binding, then idempotency. Role checks are {@code @PreAuthorize} on every endpoint; any path
 * not listed here, and any API route not registered, is refused (fail closed).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                              ZcareSecurityProperties properties, TenantRegistry tenants,
                                              SecurityEventWriter events, IdempotencyStore idempotency,
                                              ObjectMapper json) throws Exception {
        AuthenticationEntryPoint entryPoint = authenticationEntryPoint(events);
        AccessDeniedHandler deniedHandler = (request, response, denied) -> {
            events.record(SecurityEventType.AUTHORISATION_DENIED,
                    TenantContext.current().map(CurrentTenant::id).orElse(null),
                    CurrentActor.current().map(a -> a.id()).orElse(null), request, "path not permitted");
            SecurityResponses.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden");
        };
        TenantBindingFilter tenantFilter = new TenantBindingFilter(tenants, events);

        http
                // Responses carry health data: never cached or stored by the browser or a proxy
                // (Spring's default Cache-Control: no-store), never framed, and no URL is ever
                // passed on as a referrer.
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(ApiPaths.BASE + "/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(new JwtActorConverter(properties)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new IdempotencyFilter(idempotency, json), TenantBindingFilter.class);
        return http.build();
    }

    /** 401 for a missing, malformed or expired token; a presented but rejected token is also logged. */
    private static AuthenticationEntryPoint authenticationEntryPoint(SecurityEventWriter events) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, failure) -> {
            if (request.getHeader("Authorization") != null) {
                events.record(SecurityEventType.AUTHENTICATION_FAILED, null, null, request, failure.getMessage());
            }
            bearer.commence(request, response, failure); // sets WWW-Authenticate and 401
            SecurityResponses.write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
        };
    }
}
