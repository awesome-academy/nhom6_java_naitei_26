package com.example.hotelmanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI hotelManagementOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Hotel Management API")
                .version("0.0.1")
                .description("OpenAPI documentation for the Hotel Management backend."))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                    .name(BEARER_AUTH_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the access token returned by /api/auth/login or /api/auth/register.")));
    }
}
