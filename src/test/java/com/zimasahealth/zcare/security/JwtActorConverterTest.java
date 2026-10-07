package com.zimasahealth.zcare.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.context.Actor;
import com.zimasahealth.zcare.common.context.ZcareRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtActorConverterTest {

    private final JwtActorConverter converter = new JwtActorConverter(new ZcareSecurityProperties(
            new ZcareSecurityProperties.Jwt(null, null, "zimasa-zcare-service", null),
            new ZcareSecurityProperties.Claims("tenant", "org")));

    @Test
    void readsKeycloakRolesTenantAndOrganisation() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("care-manager", "offline_access")),
                "resource_access", Map.of("zimasa-zcare-service", Map.of("roles", List.of("CLINICIAN"))),
                "tenant", "acme-health", "org", "42"));

        ActorAuthenticationToken token = (ActorAuthenticationToken) converter.convert(jwt);
        Actor actor = token.getPrincipal();

        assertThat(actor.id()).isEqualTo("user-1");
        assertThat(actor.roles()).containsExactlyInAnyOrder(ZcareRole.CARE_MANAGER, ZcareRole.CLINICIAN);
        assertThat(actor.primaryRole()).isEqualTo(ZcareRole.CLINICIAN);
        assertThat(actor.tenantCode()).isEqualTo("acme-health");
        assertThat(actor.organisationId()).isEqualTo(42L);
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CARE_MANAGER", "ROLE_CLINICIAN");
    }

    @Test
    void grantsNothingForUnknownRoles() {
        ActorAuthenticationToken token = (ActorAuthenticationToken) converter.convert(
                jwt(Map.of("roles", List.of("admin", "superuser"), "tenant", "acme-health")));

        assertThat(token.getAuthorities()).isEmpty();
        assertThat(token.getPrincipal().organisationId()).isNull();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
