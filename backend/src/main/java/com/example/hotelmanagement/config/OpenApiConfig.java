package com.example.hotelmanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
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
                .version("1.0.0")
                .description("API documentation for the Hotel Management backend system")
                .contact(new Contact()
                    .name("Hotel Management Team")
                    .email("support@hotelmanagement.com")))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                    .name(BEARER_AUTH_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the access token returned by POST /api/auth/login or POST /api/auth/oauth/google/callback. Token expires in 1 hour.")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
    }
}
