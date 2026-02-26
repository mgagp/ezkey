/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiConfig
 * Description: Global configuration for OpenAPI/Swagger documentation of the Crypto API.
 */

package org.ezkey.crypto.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI configuration for Ezkey Crypto API.
 *
 * <p>This configuration defines global metadata for the Swagger/OpenAPI documentation of the Ezkey
 * Crypto API. The Crypto API is a development and testing utility that exposes cryptographic
 * primitives as REST endpoints.
 *
 * <p><b>Configured features:</b>
 *
 * <ul>
 *   <li><b>API Metadata:</b> Title, version, description, contact and license
 *   <li><b>Servers:</b> Development environment
 * </ul>
 *
 * <p><b>Access URLs:</b>
 *
 * <ul>
 *   <li><b>Swagger UI:</b> http://localhost:9090/swagger-ui/index.html
 *   <li><b>OpenAPI JSON:</b> http://localhost:9090/api-docs
 *   <li><b>OpenAPI YAML:</b> http://localhost:9090/api-docs.yaml
 * </ul>
 *
 * <p><b>Warning:</b> This API is strictly for development and testing. It must never be exposed in
 * production environments as it has no authentication and provides access to sensitive
 * cryptographic operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
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
            title = "Ezkey Crypto API",
            version = "1.0.0",
            description =
                """
                Cryptographic utility API for Ezkey - Open Source MFA/Passkey Alternative

                This API exposes cryptographic primitives as REST endpoints for development
                and testing purposes only:
                - **Key Pairs**: Generate EC P-256 (secp256r1) key pairs for device simulation
                - **Proof Tokens**: Generate cryptographically secure proof tokens
                - **Sign / Validate**: Sign data and validate signatures with EC keys
                - **Encrypt / Decrypt**: Encrypt and decrypt values for debugging database columns

                ⚠️ **NOT FOR PRODUCTION USE** — No authentication is required. This API should
                only be used in secure, isolated testing environments.
                """,
            contact =
                @Contact(
                    name = "Ezkey Team",
                    email = "contributors@ezkey.org",
                    url = "https://ezkey.org"),
            license = @License(name = "MIT License", url = "https://opensource.org/licenses/MIT")),
    servers = {
      @Server(url = "http://localhost:9090", description = "Development server Crypto API")
    })
public class OpenApiConfig {}
