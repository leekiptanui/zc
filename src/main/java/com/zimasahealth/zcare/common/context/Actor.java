package com.zimasahealth.zcare.common.context;

import java.util.Set;

/**
 * The authenticated caller, taken from the token and never from request fields or headers
 * (04B section 3, hardened by OD-02).
 *
 * @param id             token subject; written to {@code created_by}, {@code actor_id} and similar
 * @param roles          the ZCare roles the token grants
 * @param organisationId {@code zc_organisation.id} carried by provider users, otherwise null
 * @param tenantCode     the tenant the token was issued for
 */
public record Actor(String id, Set<ZcareRole> roles, Long organisationId, String tenantCode) {

    public Actor {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(ZcareRole role) {
        return roles.contains(role);
    }

    /** The highest-precedence role held; used where one role must be named, such as a source. */
    public ZcareRole primaryRole() {
        for (ZcareRole role : ZcareRole.values()) {
            if (roles.contains(role)) {
                return role;
            }
        }
        throw new IllegalStateException("Actor holds no ZCare role");
    }
}
