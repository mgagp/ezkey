/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: M2mJpaConfig
 * Description: JPA configuration for M2M API repository and entity scanning.
 */

package org.ezkey.m2m;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * JPA configuration for M2M API repository and entity scanning.
 *
 * <p>Enables JPA repositories and entity scanning for the bounded contexts used by the M2M API:
 * integration (API key validation), auth attempt, enrollment (ownership checks), audit, and
 * security (Tink encryption key management).
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
      "org.ezkey.security.domain.repository",
    })
@EntityScan(
    basePackages = {
      "org.ezkey.integration.domain.entity",
      "org.ezkey.authattempt.domain.entity",
      "org.ezkey.enrollment.domain.entity",
      "org.ezkey.audit.domain.entity",
      "org.ezkey.security.domain.entity",
    })
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class M2mJpaConfig {
  // Configuration for JPA repositories and entity scanning
}
