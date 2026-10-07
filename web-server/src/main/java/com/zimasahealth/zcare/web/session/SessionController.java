package com.zimasahealth.zcare.web.session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The web app's session endpoints. Responses carry only what the screen needs, the display name
 * and roles, never the subject, tenant, organisation or a token. Sign-out is
 * {@code POST /bff/logout}, handled by Spring Security (see {@code SecurityConfig}).
 */
@RestController
public class SessionController {

    private final LocalSignIn signIn;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public SessionController(LocalSignIn signIn) {
        this.signIn = signIn;
    }

    /** Who is signed in. Always 200, so a signed-out page load raises no error in the console. */
    @GetMapping("/bff/user")
    public Map<String, Object> user(@AuthenticationPrincipal SessionUser user) {
        return view(user);
    }

    /** Local sign-in. Starts a new session, so a session id seen before sign-in is worthless after. */
    @PostMapping("/bff/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) LoginRequest body,
                                                     HttpServletRequest request, HttpServletResponse response) {
        Optional<SessionUser> user = body == null ? Optional.empty()
                : signIn.authenticate(body.username(), body.password());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(view(null));
        }

        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        request.getSession(true);

        SessionUser signedIn = user.get();
        List<SimpleGrantedAuthority> authorities = signedIn.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(signedIn, null, authorities));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(view(signedIn));
    }

    private static Map<String, Object> view(SessionUser user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("authenticated", user != null);
        if (user != null) {
            view.put("name", user.name());
            view.put("roles", user.roles());
        }
        return view;
    }

    public record LoginRequest(String username, String password) {
    }
}
