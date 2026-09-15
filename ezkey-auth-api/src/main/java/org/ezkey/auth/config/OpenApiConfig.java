/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiConfig
 *
 * Description: Global configuration for OpenAPI/Swagger documentation of the auth API.
 */

package org.ezkey.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.ezkey.openapi.OpenApiContractCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI configuration for Ezkey auth API.
 *
 * <p>This configuration defines global metadata for the Swagger/OpenAPI documentation of the Ezkey
 * authentication API. It includes project information and security configuration for mobile device
 * authentication. Runtime and deployment hosts are not declared here; canonical specs stay
 * host-neutral and Cloudflare artifacts are packaged separately.
 *
 * <p><b>Configured features:</b>
 *
 * <ul>
 *   <li><b>API Metadata:</b> Title, version, description, contact and license
 *   <li><b>Security:</b> Cryptographic signature authentication scheme
 * </ul>
 *
 * <p><b>Access URLs:</b>
 *
 * <ul>
 *   <li><b>Swagger UI:</b> http://localhost:8080/swagger-ui.html
 *   <li><b>OpenAPI JSON:</b> http://localhost:8080/api-docs
 *   <li><b>OpenAPI YAML:</b> http://localhost:8080/api-docs.yaml
 * </ul>
 *
 * <p><b>Project :</b> Ezkey - Open Source Cryptographic MFA Platform
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
            title = "Ezkey Auth API",
            version = "1.0.0",
            description =
                """
                Authentication API for Ezkey - Open Source Cryptographic MFA Platform

                This API enables mobile device authentication operations:
                - **Enrollments**: Device binding and verification for user accounts
                - **Auth Attempts**: Mobile authentication request handling and responses

                The API follows a pull-based model where mobile devices poll for pending
                authentication requests and submit cryptographic signatures for validation.
                Ezkey is intentionally distinct from FIDO2/WebAuthn and uses its own
                cryptographic MFA model. All operations use DTOs for requests and responses
                with comprehensive validation.
                """,
            contact =
                @Contact(name = "Ezkey Team", email = "info@ezkey.org", url = "https://ezkey.org"),
            license = @License(name = "MIT License", url = "https://opensource.org/licenses/MIT")))
public class OpenApiConfig {

  /**
   * Custom OpenAPI configuration with cryptographic signature authentication.
   *
   * <p>Configures the signature-based security scheme for mobile device authentication. This scheme
   * is applied globally to all auth API endpoints that require cryptographic signature validation.
   *
   * @return the OpenAPI instance configured with signature security
   */
  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "signatureAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Signature")
                        .description("Cryptographic signature authentication for mobile devices")))
        .addSecurityItem(new SecurityRequirement().addList("signatureAuth"));
  }

  /**
   * Curates tag and path order in generated OpenAPI output for Swagger UI and ReDoc consumers.
   *
   * @return presentation-order customizer
   */
  @Bean
  public OpenApiCustomizer openApiPresentationCustomizer() {
    return new OpenApiPresentationCustomizer();
  }

  /**
   * Documents public {@code security: []} and empty 429 on rate-limited device POSTs.
   *
   * @return contract customizer
   */
  @Bean
  public OpenApiCustomizer openApiContractCustomizer() {
    return OpenApiContractCustomizer.forAuthApi();
  }
}
