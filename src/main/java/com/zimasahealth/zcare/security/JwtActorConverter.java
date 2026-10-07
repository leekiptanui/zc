package com.zimasahealth.zcare.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimasahealth.zcare.common.context.Actor;
import com.zimasahealth.zcare.common.context.ZcareRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a verified token into an {@link Actor}. Roles are read from a {@code role} or
 * {@code roles} claim, from Keycloak's {@code realm_access.roles}, and from
 * {@code resource_access.<audience>.roles}; values that name no ZCare role are ignored. A token
 * with no ZCare role still authenticates but is refused by every endpoint.
 */
public class JwtActorConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ZcareSecurityProperties properties;

    public JwtActorConverter(ZcareSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<ZcareRole> roles = roles(jwt);
        Actor actor = new Actor(jwt.getSubject(), roles, organisation(jwt),
                jwt.getClaimAsString(properties.claims().tenant()));
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
        return new ActorAuthenticationToken(actor, jwt, authorities);
    }

    private Set<ZcareRole> roles(Jwt jwt) {
        Set<ZcareRole> roles = EnumSet.noneOf(ZcareRole.class);
        addRole(roles, jwt.getClaim("role"));
        addRoles(roles, jwt.getClaim("roles"));
        Object realm = jwt.getClaim("realm_access");
        if (realm instanceof Map<?, ?> realmAccess) {
            addRoles(roles, realmAccess.get("roles"));
        }
        Object resource = jwt.getClaim("resource_access");
        if (resource instanceof Map<?, ?> resourceAccess
                && resourceAccess.get(properties.jwt().audience()) instanceof Map<?, ?> client) {
            addRoles(roles, client.get("roles"));
        }
        return roles;
    }

    private static void addRoles(Set<ZcareRole> roles, Object claim) {
        if (claim instanceof Collection<?> values) {
            values.forEach(value -> addRole(roles, value));
        }
    }

    private static void addRole(Set<ZcareRole> roles, Object value) {
        if (value instanceof String code) {
            ZcareRole.fromCode(code).ifPresent(roles::add);
        }
    }

    private Long organisation(Jwt jwt) {
        Object value = jwt.getClaim(properties.claims().organisation());
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && text.matches("\\d{1,18}")) {
            return Long.parseLong(text);
        }
        return null;
    }
}
