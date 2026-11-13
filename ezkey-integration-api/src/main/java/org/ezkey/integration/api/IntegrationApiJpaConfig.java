/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: IntegrationApiJpaConfig
 * Description: JPA configuration for Integration API repository and entity scanning.
 */

package org.ezkey.integration.api;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * JPA configuration for Integration API repository and entity scanning.
 *
 * <p>Enables JPA repositories and entity scanning for the bounded contexts used by the Integration
 * API: integration (API key validation), auth attempt, enrollment (ownership checks), audit, and
 * security (Tink encryption key management).
 *
 * <p><b>Least privilege:</b> does <strong>not</strong> scan {@code org.ezkey.audit.asyncjob}
 * (Admin-only Integrity async job entity). Integration must not map or grant on {@code
 * ezkey_integrity_async_job}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableJpaRepositories(
    basePackages = {
      "org.ezkey.integration.domain.repository",
      "org.ezkey.authattempt.domain.repository",
      "org.ezkey.enrollment.domain.repository",
      "org.ezkey.audit.domain.repository",
      "org.ezkey.audit.integrity",
      "org.ezkey.security.domain.repository",
      "org.ezkey.alert.repository",
    })
@EntityScan(
    basePackages = {
      "org.ezkey.integration.domain.entity",
      "org.ezkey.authattempt.domain.entity",
      "org.ezkey.enrollment.domain.entity",
      "org.ezkey.audit.domain.entity",
      "org.ezkey.audit.integrity",
      "org.ezkey.security.domain.entity",
      "org.ezkey.alert.domain.entity",
    })
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class IntegrationApiJpaConfig {
  // Configuration for JPA repositories and entity scanning
}
