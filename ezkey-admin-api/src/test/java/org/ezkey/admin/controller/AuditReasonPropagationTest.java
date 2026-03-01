/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditReasonPropagationTest
 * Description: Unit tests verifying that the optional `reason` justification field is correctly
 *     propagated to AuditLog entries for SOC 2 CC6.3 / CC8.1 compliance.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.service.ApiKeyService;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests verifying that the optional {@code reason} justification field is correctly propagated
 * to {@link AuditLog} entries for SOC 2 CC6.3 / CC8.1 compliance.
 *
 * <p>Tests cover three controllers that accept a {@code reason} query parameter on mutating
 * operations: {@link ApiKeyController}, {@link EnrollmentController}, and {@link
 * EncryptionKeyController}.
 *
 * <p>No Spring context is loaded — controllers are instantiated directly to keep tests fast and
 * independent of infrastructure concerns.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Audit Reason Field Propagation Tests")
class AuditReasonPropagationTest {

  // --- Shared mocks ---

  @Mock private AuditLogService auditLogService;
  @Mock private HttpServletRequest httpRequest;

  // --- ApiKeyController mocks ---

  @Mock private ApiKeyService apiKeyService;
  @Mock private AdminOperationsRateLimitService adminOpsRateLimitService;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AccessControlService accessControlService;

  // --- EnrollmentController mocks ---

  @Mock private EnrollmentService enrollmentService;
  @Mock private EnrollmentAdminMapper enrollmentMapper;
  @Mock private QrCodeGeneratorService qrCodeGeneratorService;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;

  // --- EncryptionKeyController mocks ---

  @Mock private EncryptionKeyRepository encryptionKeyRepository;
  @Mock private ReencryptionBatchRepository reencryptionBatchRepository;
  @Mock private KeyRotationService rotationService;
  @Mock private ReencryptionService reencryptionService;

  @BeforeEach
  void setUpHttpRequest() {
    // AuditHelper.extractClientIp reads several headers, then falls back to
    // remoteAddr
    when(httpRequest.getHeader("CF-Connecting-IP")).thenReturn(null);
    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
  }

  // -------------------------------------------------------------------------
  // ApiKeyController – revokeApiKey
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("revokeApiKey() with reason – AuditLog.reason is populated")
  void revokeApiKey_withReason_propagatesReasonToAuditLog() {
    // Arrange
    setupAdminSecurityContext();
    ApiKeyController controller =
        new ApiKeyController(
            apiKeyService,
            adminOpsRateLimitService,
            adminRepository,
            accessControlService,
            auditLogService);

    when(apiKeyService.revokeApiKey(eq(42), any(EzkeyAdmin.class))).thenReturn(true);

    // Act
    controller.revokeApiKey(42, "Compromised in incident #42", httpRequest);

    // Assert
    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());

    AuditLog logged = captor.getValue();
    assertEquals(EventType.API_KEY_REVOKED, logged.getEventType());
    assertEquals(EventStatus.SUCCESS, logged.getEventStatus());
    assertEquals("Compromised in incident #42", logged.getReason());
  }

  @Test
  @DisplayName("revokeApiKey() without reason – AuditLog.reason is null")
  void revokeApiKey_withoutReason_auditReasonIsNull() {
    // Arrange
    setupAdminSecurityContext();
    ApiKeyController controller =
        new ApiKeyController(
            apiKeyService,
            adminOpsRateLimitService,
            adminRepository,
            accessControlService,
            auditLogService);

    when(apiKeyService.revokeApiKey(eq(42), any(EzkeyAdmin.class))).thenReturn(true);

    // Act
    controller.revokeApiKey(42, null, httpRequest);

    // Assert
    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());

    assertNull(captor.getValue().getReason());
  }

  // -------------------------------------------------------------------------
  // EnrollmentController – delete
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("delete() enrollment with reason – AuditLog.reason is populated")
  void deleteEnrollment_withReason_propagatesReasonToAuditLog() {
    // Arrange
    Authentication auth = org.mockito.Mockito.mock(Authentication.class);
    SecurityContextHolder.getContext().setAuthentication(auth);

    when(accessControlService.canAccessEnrollment(any(Authentication.class), eq(42)))
        .thenReturn(true);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(42);
    enrollment.setIntegrationId(1);
    enrollment.setEnrollmentName("Test Device");
    when(enrollmentService.getById(42)).thenReturn(enrollment);

    // integrationRepository returns empty → tenantId resolves to null (acceptable
    // for test)
    when(integrationRepository.findById(1)).thenReturn(Optional.empty());

    EnrollmentController controller =
        new EnrollmentController(
            enrollmentService,
            enrollmentMapper,
            auditLogService,
            qrCodeGeneratorService,
            qrCodePayloadService,
            accessControlService,
            enrollmentRepository,
            integrationRepository);

    // Act
    controller.delete(42, "Decommissioned device returned", httpRequest);

    // Assert
    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    // 1 log call: the SUCCESS audit written before deletion
    verify(auditLogService, times(1)).log(captor.capture());

    AuditLog logged = captor.getValue();
    assertEquals(EventType.ENROLLMENT_DELETED, logged.getEventType());
    assertEquals(EventStatus.SUCCESS, logged.getEventStatus());
    assertEquals("Decommissioned device returned", logged.getReason());
  }

  // -------------------------------------------------------------------------
  // EncryptionKeyController – rotateKey
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("rotateKey() with reason – AuditLog.reason is populated")
  void rotateKey_withReason_propagatesReasonToAuditLog() throws Exception {
    // Arrange
    EncryptionKeyController controller =
        new EncryptionKeyController(
            encryptionKeyRepository,
            reencryptionBatchRepository,
            rotationService,
            reencryptionService,
            auditLogService);

    when(rotationService.introduceNewKey("ADMIN_MANUAL")).thenReturn(12345L);

    // Act
    controller.rotateKey("Annual key rotation policy", httpRequest);

    // Assert
    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());

    AuditLog logged = captor.getValue();
    assertEquals(EventType.KEY_INTRODUCED, logged.getEventType());
    assertEquals(EventStatus.SUCCESS, logged.getEventStatus());
    assertEquals("Annual key rotation policy", logged.getReason());
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  /**
   * Configures the Spring SecurityContext with a mocked GLOBAL_ADMIN principal so that controllers
   * that call {@code getCurrentAdmin()} can resolve the admin entity.
   */
  private void setupAdminSecurityContext() {
    Authentication auth = org.mockito.Mockito.mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getAuthorities())
        .thenAnswer(inv -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    when(auth.getPrincipal()).thenReturn(new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null));
    SecurityContextHolder.getContext().setAuthentication(auth);

    EzkeyAdmin admin = org.mockito.Mockito.mock(EzkeyAdmin.class);
    when(admin.getAdminType()).thenReturn(AdminType.GLOBAL_ADMIN);
    when(adminRepository.findById(1)).thenReturn(Optional.of(admin));
  }
}
