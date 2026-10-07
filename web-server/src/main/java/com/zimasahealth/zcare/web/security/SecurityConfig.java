package com.zimasahealth.zcare.web.security;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.zimasahealth.zcare.web.config.WebServerProperties;

/**
 * Browser-facing security (ADR-0007).
 * <ul>
 *   <li>The session lives on this server; the browser holds only the session cookie, which is
 *       {@code HttpOnly} and {@code SameSite=Strict} (see {@code server.servlet.session.cookie}).</li>
 *   <li>Every state-changing call needs the anti-CSRF header {@code X-XSRF-TOKEN}, copied from the
 *       {@code XSRF-TOKEN} cookie.</li>
 *   <li>Unauthenticated calls get 401, never a redirect, so the web app shows its own sign-in.</li>
 *   <li>Responses are never cached, never sent as a referrer and never framed, and carry a strict
 *       Content-Security-Policy.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, WebServerProperties properties,
                                            ServerProperties server) throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> cookie
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path("/"));
        String sessionCookie = server.getServlet().getSession().getCookie().getName();

        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .requestCache(cache -> cache.disable())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/bff/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies(sessionCookie == null ? "JSESSIONID" : sessionCookie))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(properties.contentSecurityPolicy()))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }
}
