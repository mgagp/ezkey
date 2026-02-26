/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyAuthJpaConfig
 *
 * Description: JPA configuration for Ezkey Auth API.
 */

package org.ezkey.auth;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for Ezkey Auth API.
 *
 * <p>This configuration enables JPA repositories and entity scanning for all domains used by the
 * auth API: authattempt, enrollment, integration, audit, and security (for encryption key
 * management services).
 *
 * @since 2025
 */
@Configuration
@EnableJpaRepositories(
    basePackages = { //
      "org.ezkey.integration.domain.repository", //
      "org.ezkey.authattempt.domain.repository", //
      "org.ezkey.enrollment.domain.repository", //
      "org.ezkey.audit.domain.repository", //
      "org.ezkey.audit.integrity", //
      "org.ezkey.security.domain.repository", //
    })
@EntityScan(
    basePackages = { //
      "org.ezkey.integration.domain.entity", //
      "org.ezkey.authattempt.domain.entity", //
      "org.ezkey.enrollment.domain.entity", //
      "org.ezkey.audit.domain.entity", //
      "org.ezkey.audit.integrity", //
      "org.ezkey.security.domain.entity", //
    })
public class AuthJpaConfig {
  // Configuration for JPA repositories and entity scanning
}
