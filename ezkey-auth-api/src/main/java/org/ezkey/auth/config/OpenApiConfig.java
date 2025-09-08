/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiConfig
 * Description: Global configuration for OpenAPI/Swagger documentation of the auth API.
 */

package org.ezkey.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;


/**
 * Global OpenAPI configuration for Ezkey auth API.
 * <p>
 * This configuration defines global metadata for the Swagger/OpenAPI documentation
 * of the Ezkey authentication API. It includes project information, available servers,
 * and security configuration for mobile device authentication.
 * </p>
 *
 * <p>
 * <b>Configured features:</b>
 * <ul>
 * <li><b>API Metadata:</b> Title, version, description, contact and license</li>
 * <li><b>Servers:</b> Development and production environments</li>
 * <li><b>Security:</b> Cryptographic signature authentication scheme</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Access URLs:</b>
 * <ul>
 * <li><b>Swagger UI:</b> http://localhost:8080/swagger-ui.html</li>
 * <li><b>OpenAPI JSON:</b> http://localhost:8080/api-docs</li>
 * <li><b>OpenAPI YAML:</b> http://localhost:8080/api-docs.yaml</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project :</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License :</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see io.swagger.v3.oas.annotations.OpenAPIDefinition
 * @see org.springframework.context.annotation.Configuration
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Ezkey Auth API",
        version = "1.0.0",
        description = """
            Authentication API for Ezkey - Open Source MFA/Passkey Alternative
            
            This API enables mobile device authentication operations:
            - **Enrollments**: Device binding and verification for user accounts
            - **Auth Attempts**: Mobile authentication request handling and responses
            
            The API follows a pull-based model where mobile devices poll for pending
            authentication requests and submit cryptographic signatures for validation.
            All operations use DTOs for requests and responses with comprehensive validation.
            """,
        contact = @Contact(
            name = "Ezkey Team",
            email = "contributors@ezkey.org",
            url = "https://ezkey.org"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development server Auth API"),
        @Server(url = "https://auth-api.ezkey.org", description = "Production server Auth API")
    }
)
public class OpenApiConfig {
    
    /**
     * Custom OpenAPI configuration with cryptographic signature authentication.
     * <p>
     * Configures the signature-based security scheme for mobile device authentication.
     * This scheme is applied globally to all auth API endpoints that require
     * cryptographic signature validation.
     * </p>
     *
     * @return the OpenAPI instance configured with signature security
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("signatureAuth", 
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Signature")
                        .description("Cryptographic signature authentication for mobile devices")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("signatureAuth"));
    }
}
