package com.zimasahealth.zcare.web.session;

import java.io.Serializable;
import java.util.List;

/**
 * The signed-in user, held in the server-side session. Only {@link #name} and {@link #roles}
 * ever reach the browser; the subject, tenant and organisation stay on the server.
 *
 * @param subject      the actor id the API sees as {@code sub}
 * @param name         display name
 * @param tenant       tenant code
 * @param organisation a provider user's organisation id, or null
 * @param roles        ZCare role codes
 */
public record SessionUser(String subject, String name, String tenant, Long organisation, List<String> roles)
        implements Serializable {

    public SessionUser {
        roles = List.copyOf(roles);
    }

    /** Never prints the tenant or organisation, so a stray log line cannot leak them. */
    @Override
    public String toString() {
        return "SessionUser[" + subject + "]";
    }
}
