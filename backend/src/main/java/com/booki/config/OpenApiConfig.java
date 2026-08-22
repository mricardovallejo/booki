package com.booki.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the live spec (served at /v3/api-docs and /swagger-ui.html once the
 * app is running) aligned with the hand-written design contract at
 * docs/openapi.yaml — same title, version, and Bearer auth scheme. The
 * hand-written file is the upfront design; this is the generated proof that
 * the running code actually matches it.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI kobiBackendApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("kobi-backend-api")
                        .version("1.0.0")
                        .description("Live-generated contract for BooKI's backend. See docs/openapi.yaml in the repo for the hand-written design contract this must stay in sync with.")
                        .license(new License().name("Proprietary — internal use only")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
