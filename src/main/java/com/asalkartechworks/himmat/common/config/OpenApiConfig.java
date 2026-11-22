package com.asalkartechworks.himmat.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI himmatOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Himmat Enterprises Backend API")
                        .description("Backend APIs for Himmat Enterprises shop management system - authentication, user management, shop settings, product catalog, customer & supplier masters, inventory management, stock movements, and manual adjustments.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Asalkar Techworks Private Limited")
                                .email("contact@asalkartechworks.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT Bearer token obtained from POST /api/v1/auth/login")));
    }
}
