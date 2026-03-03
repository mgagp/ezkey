/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminJpaConfig
 * Description: JPA configuration for admin API repository and entity scanning.
 */

package org.ezkey.admin;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * JPA configuration for admin API repository and entity scanning.
 *
 * <p>This configuration class enables JPA repositories and entity scanning for all domain modules
 * used by the admin API. It centralizes the configuration of Spring Data JPA repositories and
 * entity discovery across multiple bounded contexts (integration, auth attempt, enrollment, and
 * audit).
 *
 * <p><b>Configured Repositories:</b>
 *
 * <ul>
 *   <li><b>Integration:</b> Repositories for integration and admin management
 *   <li><b>Auth Attempt:</b> Repositories for authentication attempt operations
 *   <li><b>Enrollment:</b> Repositories for device enrollment management
 *   <li><b>Audit:</b> Repositories for audit logging
 *   <li><b>Audit Integrity:</b> Repositories for audit chain checkpoint management
 *   <li><b>Security:</b> Repositories for encryption key and re-encryption batch management
 * </ul>
 *
 * <p><b>Configured Entities:</b>
 *
 * <ul>
 *   <li><b>Integration Entities:</b> Integration, EzkeyAdmin, AdminToken, ApiKey
 *   <li><b>Auth Attempt Entities:</b> AuthAttempt and related entities
 *   <li><b>Enrollment Entities:</b> Enrollment and related entities
 *   <li><b>Audit Entities:</b> AuditLog and related entities
 *   <li><b>Audit Integrity Entities:</b> AuditChainCheckpoint
 *   <li><b>Security Entities:</b> EncryptionKey, ReencryptionBatch
 * </ul>
 *
 * <p><b>Usage Context:</b> This configuration is automatically detected by Spring Boot and applies
 * to the entire admin API module. It ensures that all JPA repositories and entities are properly
 * scanned and registered for dependency injection and persistence operations.
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
      "org.ezkey.audit.integrity",
      "org.ezkey.security.domain.repository"
    })
@EntityScan(
    basePackages = {
      "org.ezkey.integration.domain.entity",
      "org.ezkey.authattempt.domain.entity",
      "org.ezkey.enrollment.domain.entity",
      "org.ezkey.audit.domain.entity",
      "org.ezkey.audit.integrity",
      "org.ezkey.security.domain.entity"
    })
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class AdminJpaConfig {
  // Configuration for JPA repositories and entity scanning
}
