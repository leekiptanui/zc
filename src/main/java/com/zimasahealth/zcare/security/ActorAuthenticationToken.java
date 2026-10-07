package com.zimasahealth.zcare.security;

import java.util.Collection;

import com.zimasahealth.zcare.common.context.Actor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** An authenticated request: the {@link Actor} as principal, the verified token as credentials. */
public class ActorAuthenticationToken extends AbstractAuthenticationToken {

    private final Actor actor;
    private final Jwt jwt;

    public ActorAuthenticationToken(Actor actor, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.actor = actor;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Actor getPrincipal() {
        return actor;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return actor.id();
    }
}
