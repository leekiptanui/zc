package com.zimasahealth.zcare.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Full token validation (M02-15): signature, expiry, and issuer and audience when configured.
 * Keys come from Keycloak through the issuer or a JWK set; a shared HS256 secret is accepted for
 * local runs and tests only. With nothing configured the service refuses to start (fail closed).
 */
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfig {

    static final int MIN_HMAC_SECRET_BYTES = 32;

    @Bean
    public JwtDecoder jwtDecoder(ZcareSecurityProperties properties) {
        ZcareSecurityProperties.Jwt jwt = properties.jwt();
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(jwt.hmacSecret())) {
            byte[] secret = jwt.hmacSecret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < MIN_HMAC_SECRET_BYTES) {
                throw new IllegalStateException("zcare.security.jwt.hmac-secret must be at least "
                        + MIN_HMAC_SECRET_BYTES + " bytes");
            }
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        } else if (StringUtils.hasText(jwt.jwkSetUri())) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwt.jwkSetUri()).build();
        } else if (StringUtils.hasText(jwt.issuerUri())) {
            decoder = NimbusJwtDecoder.withIssuerLocation(jwt.issuerUri()).build();
        } else {
            throw new IllegalStateException("No token verification configured: set KEYCLOAK_ISSUER_URI"
                    + " (zcare.security.jwt.issuer-uri) or, for local runs only, ZCARE_JWT_HMAC_SECRET");
        }

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (StringUtils.hasText(jwt.issuerUri())) {
            validators.add(new JwtIssuerValidator(jwt.issuerUri()));
        }
        if (StringUtils.hasText(jwt.audience())) {
            validators.add(audience(jwt.audience()));
        }
        validators.add(requiredClaims());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audience(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "The token is not issued for " + audience, null);
        return token -> token.getAudience() != null && token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }

    private static OAuth2TokenValidator<Jwt> requiredClaims() {
        OAuth2Error error = new OAuth2Error("invalid_token", "The token must carry sub and exp", null);
        return token -> StringUtils.hasText(token.getSubject()) && token.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
