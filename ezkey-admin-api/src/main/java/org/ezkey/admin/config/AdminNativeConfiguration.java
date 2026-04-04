/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminNativeConfiguration
 *
 * Description: Native image configuration and AOT hints for Ezkey Admin API.
 */

package org.ezkey.admin.config;

import org.ezkey.admin.controller.AdminAuthController;
import org.ezkey.admin.controller.AdminEnrollmentController;
import org.ezkey.admin.controller.ApiKeyController;
import org.ezkey.admin.controller.AuditLogController;
import org.ezkey.admin.controller.AuthAttemptController;
import org.ezkey.admin.controller.EncryptionKeyController;
import org.ezkey.admin.controller.EnrollmentController;
import org.ezkey.admin.controller.IntegrationController;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordlessWaitRequestDto;
import org.ezkey.admin.dto.request.AdminRecoveryRequestDto;
import org.ezkey.admin.dto.request.ApiKeyCreateRequestDto;
import org.ezkey.admin.dto.request.EnrollmentResetRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminRecoveryResponseDto;
import org.ezkey.admin.dto.response.ApiKeyCreateResponseDto;
import org.ezkey.admin.dto.response.ApiKeyResponseDto;
import org.ezkey.admin.dto.response.EnrollmentResetResponseDto;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.ezkey.dto.ErrorResponseDto;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.dto.EnrollmentCreateResponseDto;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.dto.IntegrationCreateResponseDto;
import org.ezkey.integration.dto.IntegrationResponseDto;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Native image configuration for Ezkey Admin API.
 *
 * <p>This configuration provides AOT hints and runtime configuration needed for native image
 * compilation. It includes reflection configuration for DTOs, resource access patterns, and other
 * native image requirements.
 *
 * <p><b>AOT Processing:</b>
 *
 * <ul>
 *   <li><b>Reflection:</b> All DTOs used in REST endpoints, controllers, and entities
 *   <li><b>Resources:</b> Application properties and validation messages
 *   <li><b>Serialization:</b> Jackson serialization for all DTOs and entities
 * </ul>
 *
 * <p><b>Configuration Approach:</b> This configuration uses explicit Java-based hints rather than
 * JSON files for better maintainability, type safety, and explicit control over native image
 * compilation requirements.
 *
 * @since 2025
 */
@Configuration
@ImportRuntimeHints(AdminNativeConfiguration.AdminRuntimeHints.class)
public class AdminNativeConfiguration {

  /**
   * Runtime hints registrar for native image compilation.
   *
   * <p>Registers all DTOs, controllers, entities, and classes that need reflection access during
   * native image runtime. This includes all request/response DTOs used by the REST endpoints, JPA
   * entities, and exception classes.
   */
  static class AdminRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      // Register application main class for reflection
      hints.reflection().registerType(org.ezkey.admin.AdminApplication.class);

      // Register controllers for reflection
      hints
          .reflection()
          .registerType(IntegrationController.class)
          .registerType(AuthAttemptController.class)
          .registerType(EnrollmentController.class)
          .registerType(AdminEnrollmentController.class)
          .registerType(AdminAuthController.class)
          .registerType(ApiKeyController.class)
          .registerType(EncryptionKeyController.class)
          .registerType(AuditLogController.class);

      // Register GlobalExceptionHandler for reflection
      hints.reflection().registerType(org.ezkey.exception.GlobalExceptionHandler.class);

      // Register JPA entities for reflection
      hints
          .reflection()
          .registerType(org.ezkey.authattempt.domain.entity.AuthAttempt.class)
          .registerType(org.ezkey.enrollment.domain.entity.Enrollment.class)
          .registerType(org.ezkey.integration.domain.entity.Integration.class)
          .registerType(org.ezkey.integration.domain.entity.ApiKey.class)
          .registerType(org.ezkey.integration.domain.entity.EzkeyAdmin.class)
          .registerType(org.ezkey.integration.domain.entity.Tenant.class)
          .registerType(org.ezkey.integration.domain.entity.AdminToken.class)
          .registerType(org.ezkey.security.domain.entity.EncryptionKey.class)
          .registerType(org.ezkey.security.domain.entity.ReencryptionBatch.class)
          .registerType(org.ezkey.security.domain.entity.KeysetBlob.class)
          .registerType(org.ezkey.audit.domain.entity.AuditLog.class);

      // Register Integration DTOs for reflection
      hints
          .reflection()
          .registerType(IntegrationCreateRequestDto.class)
          .registerType(IntegrationCreateResponseDto.class)
          .registerType(IntegrationResponseDto.class);

      // Register AuthAttempt DTOs for reflection
      hints
          .reflection()
          .registerType(AuthAttemptDto.class)
          .registerType(AuthAttemptCreateRequestDto.class)
          .registerType(AuthAttemptCreateResponseDto.class)
          .registerType(AuthAttemptWaitRequestDto.class)
          .registerType(AuthAttemptWaitResponseDto.class);

      // Register Enrollment DTOs for reflection
      hints
          .reflection()
          .registerType(EnrollmentCreateRequestDto.class)
          .registerType(EnrollmentCreateResponseDto.class)
          .registerType(EnrollmentResponseDto.class);

      // Register Admin DTOs for reflection
      hints
          .reflection()
          .registerType(AdminLoginRequestDto.class)
          .registerType(AdminLoginResponseDto.class)
          .registerType(AdminPasswordlessWaitRequestDto.class)
          .registerType(AdminRecoveryRequestDto.class)
          .registerType(AdminRecoveryResponseDto.class)
          .registerType(EnrollmentResetRequestDto.class)
          .registerType(EnrollmentResetResponseDto.class);

      // Register API Key DTOs for reflection
      hints
          .reflection()
          .registerType(ApiKeyCreateRequestDto.class)
          .registerType(ApiKeyCreateResponseDto.class)
          .registerType(ApiKeyResponseDto.class);

      // Register EncryptionKeyController record DTOs for reflection
      // These are defined as public record classes in EncryptionKeyController
      // Using TypeReference.of with class name string for inner classes
      hints
          .reflection()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$EncryptionKeyResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$KeyRotationResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$ReencryptionBatchResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$BatchResumeResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$ReencryptionTriggerResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$ReencryptionKeyResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$BatchCreationResponse"));

      // Register exception classes for reflection (used by GlobalExceptionHandler)
      hints
          .reflection()
          .registerType(org.ezkey.exception.ResourceNotFoundException.class)
          .registerType(org.ezkey.exception.SystemTenantNotConfiguredException.class)
          .registerType(org.ezkey.integration.exception.ApiKeyCreateValidationException.class)
          .registerType(org.ezkey.integration.exception.ApiKeyIpWhitelistValidationException.class)
          .registerType(org.ezkey.integration.exception.ApiKeyUpdateValidationException.class)
          .registerType(org.ezkey.integration.exception.IntegrationCreateValidationException.class)
          .registerType(org.ezkey.exception.RateLimitExceededException.class)
          .registerType(org.ezkey.exception.NoPendingAuthAttemptException.class)
          .registerType(org.ezkey.admin.exception.AuthenticationException.class)
          .registerType(org.ezkey.security.exception.PendingEncryptionKeyExistsException.class);

      // Register AuditLog DTOs for reflection
      hints.reflection().registerType(AuditLogResponseDto.class);

      // Register ErrorResponseDto for reflection (used by GlobalExceptionHandler)
      hints.reflection().registerType(ErrorResponseDto.class);

      // Register serialization hints for Jackson - Integration DTOs
      hints
          .serialization()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(IntegrationCreateRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(IntegrationCreateResponseDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(IntegrationResponseDto.class));

      // Register serialization hints for Jackson - AuthAttempt DTOs
      hints
          .serialization()
          .registerType(org.springframework.aot.hint.TypeReference.of(AuthAttemptDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptCreateRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptCreateResponseDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptWaitRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptWaitResponseDto.class));

      // Register serialization hints for Jackson - Enrollment DTOs
      hints
          .serialization()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentCreateRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentCreateResponseDto.class))
          .registerType(org.springframework.aot.hint.TypeReference.of(EnrollmentResponseDto.class));

      // Register serialization hints for Jackson - Admin DTOs
      hints
          .serialization()
          .registerType(org.springframework.aot.hint.TypeReference.of(AdminLoginRequestDto.class))
          .registerType(org.springframework.aot.hint.TypeReference.of(AdminLoginResponseDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AdminPasswordlessWaitRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AdminRecoveryRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AdminRecoveryResponseDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentResetRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentResetResponseDto.class));

      // Register serialization hints for Jackson - API Key DTOs
      hints
          .serialization()
          .registerType(org.springframework.aot.hint.TypeReference.of(ApiKeyCreateRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(ApiKeyCreateResponseDto.class))
          .registerType(org.springframework.aot.hint.TypeReference.of(ApiKeyResponseDto.class));

      // Register serialization hints for Jackson - EncryptionKeyController record DTOs
      hints
          .serialization()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$EncryptionKeyResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$KeyRotationResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$ReencryptionBatchResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$BatchResumeResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$ReencryptionTriggerResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$ReencryptionKeyResponse"))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.admin.controller.EncryptionKeyController$BatchCreationResponse"));

      // Register serialization hints for Jackson - AuditLog DTOs
      hints
          .serialization()
          .registerType(org.springframework.aot.hint.TypeReference.of(AuditLogResponseDto.class));

      // Register serialization hints for Jackson - ErrorResponseDto
      hints
          .serialization()
          .registerType(org.springframework.aot.hint.TypeReference.of(ErrorResponseDto.class));

      // Register serialization hints for Jackson - JPA Entities
      hints
          .serialization()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.authattempt.domain.entity.AuthAttempt.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.enrollment.domain.entity.Enrollment.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.integration.domain.entity.Integration.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.integration.domain.entity.ApiKey.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.integration.domain.entity.EzkeyAdmin.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.integration.domain.entity.Tenant.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.integration.domain.entity.AdminToken.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.security.domain.entity.EncryptionKey.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.security.domain.entity.ReencryptionBatch.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.security.domain.entity.KeysetBlob.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.audit.domain.entity.AuditLog.class));

      // Register resource patterns
      hints
          .resources()
          .registerPattern("application*.properties")
          .registerPattern("META-INF/native-image/org.ezkey/ezkey-admin-api/*")
          .registerPattern("ValidationMessages.properties");
    }
  }
}
