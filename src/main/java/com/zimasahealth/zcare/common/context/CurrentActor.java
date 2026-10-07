package com.zimasahealth.zcare.common.context;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the authenticated {@link Actor} from the security context. */
public final class CurrentActor {

    /** Recorded as {@code created_by} for writes that no person made, such as security events. */
    public static final String SYSTEM = "system";

    private CurrentActor() {
    }

    public static Optional<Actor> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Actor actor) {
            return Optional.of(actor);
        }
        return Optional.empty();
    }

    public static Actor require() {
        return current().orElseThrow(() -> new IllegalStateException("No authenticated actor"));
    }

    /** The actor id, or {@link #SYSTEM} outside an authenticated request. */
    public static String idOrSystem() {
        return current().map(Actor::id).orElse(SYSTEM);
    }
}
