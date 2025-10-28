/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiConfig
 * Description: Global configuration for OpenAPI/Swagger documentation of the admin API.
 */

package org.ezkey.admin.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI configuration for Ezkey admin API.
 *
 * <p>This configuration defines global metadata for the Swagger/OpenAPI documentation of the Ezkey
 * administration API. It includes project information, available servers, and security
 * configuration for JWT authentication.
 *
 * <p><b>Configured features:</b>
 *
 * <ul>
 *   <li><b>API Metadata:</b> Title, version, description, contact and license
 *   <li><b>Servers:</b> Development and production environments
 *   <li><b>Security:</b> Bearer JWT authentication scheme
 * </ul>
 *
 * <p><b>Access URLs:</b>
 *
 * <ul>
 *   <li><b>Swagger UI:</b> http://localhost:9080/swagger-ui.html
 *   <li><b>OpenAPI JSON:</b> http://localhost:9080/api-docs
 *   <li><b>OpenAPI YAML:</b> http://localhost:9080/api-docs.yaml
 * </ul>
 *
 * <p><b>Project :</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License :</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see io.swagger.v3.oas.annotations.OpenAPIDefinition
 * @see org.springframework.context.annotation.Configuration
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Ezkey Admin API",
            version = "1.0.0",
            description =
                """
                Administration API for Ezkey - Open Source MFA/Passkey Alternative

                This API enables administrative management of Ezkey's main entities:
                - **Integrations**: Applications or systems protected by MFA
                - **Enrollments**: Associations between users, devices and integrations
                - **Auth Attempts**: MFA authentication attempts
                - **Admin Management**: Administrator authentication, enrollment recovery, and admin operations

                The API follows REST conventions and uses DTOs for all requests and responses.
                """,
            contact =
                @Contact(
                    name = "Ezkey Team",
                    email = "contributors@ezkey.org",
                    url = "https://ezkey.org"),
            license = @License(name = "MIT License", url = "https://opensource.org/licenses/MIT")),
    servers = {
      @Server(url = "http://localhost:9080", description = "Development server Admin API"),
      @Server(url = "https://admin-api.ezkey.org", description = "Production server Admin API")
    })
public class OpenApiConfig {

  /**
   * Custom OpenAPI configuration with JWT authentication.
   *
   * <p>Configures the Bearer JWT security scheme for the entire API. This scheme is applied
   * globally to all admin API endpoints.
   *
   * @return the OpenAPI instance configured with JWT security
   */
  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT authentication token for admin API")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
