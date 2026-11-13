/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiConfig
 * Description: OpenAPI / Swagger documentation configuration for Integration API.
 */

package org.ezkey.integration.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.ezkey.openapi.OpenApiContractCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation configuration for the Ezkey Integration API.
 *
 * <p>Exposes a Swagger UI with HTTP Basic (API key) security scheme pre-configured.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
public class OpenApiConfig {

  /**
   * Builds the OpenAPI specification for the Integration API.
   *
   * @return the configured {@link OpenAPI} instance
   */
  @Bean
  public OpenAPI integrationApiOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ezkey Integration API")
                .description(
                    "Integration API for auth attempt lifecycle operations (create / wait / cancel)"
                        + " authenticated via API key (machine-to-machine). Ezkey is intentionally"
                        + " distinct from FIDO2/WebAuthn and uses its own cryptographic MFA model.")
                .version("1.0.0")
                .contact(
                    new Contact()
                        .name("Ezkey Team")
                        .email("info@ezkey.org")
                        .url("https://ezkey.org"))
                .license(
                    new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
        .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "ApiKeyAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description(
                            "HTTP Basic Auth — username: ezkey_ikey_xxx, "
                                + "password: ezkey_skey_xxx")));
  }

  /**
   * Curates path order in generated OpenAPI output for Swagger UI and ReDoc consumers.
   *
   * @return presentation-order customizer
   */
  @Bean
  public OpenApiCustomizer openApiPresentationCustomizer() {
    return new OpenApiPresentationCustomizer();
  }

  /**
   * Documents empty 401 from the API-key authentication entry point.
   *
   * @return contract customizer
   */
  @Bean
  public OpenApiCustomizer openApiContractCustomizer() {
    return OpenApiContractCustomizer.forIntegrationApi();
  }
}
