package com.zimasahealth.zcare.config;

import java.util.Optional;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document generated from code (M02-17), served at {@code /v3/api-docs} with Swagger UI
 * at {@code /swagger-ui.html}. {@code info.version} comes from the build.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI zcareOpenApi(ObjectProvider<BuildProperties> build) {
        String version = Optional.ofNullable(build.getIfAvailable()).map(BuildProperties::getVersion).orElse("dev");
        return new OpenAPI()
                .info(new Info()
                        .title("ZCare API")
                        .version(version)
                        .description("""
                                Care and disease management between clinic visits (04B). Every response is the \
                                ENG-STD-SB-001 envelope; business exceptions are HTTP 200 with status "exception". \
                                Every mutating call needs an Idempotency-Key (UUID). The tenant is taken only from the \
                                verified token; it is never part of a URL."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
