/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentControllerQrAccessTest
 * Description: Unit tests for enrollment QR tenant access (CTRL-ROLE-001 hide-existence).
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.admin.service.EnrollmentUpdateService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@code GET /api/v1/enrollments/{id}/qrcode} tenant access.
 *
 * <p>Foreign Tenant Admins must receive 404 (hide-existence), not 403 and not 200. Owning admins
 * still receive the PNG. Auth API bind is unchanged.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentController QR tenant access")
class EnrollmentControllerQrAccessTest {

  private static final Integer ENROLLMENT_ID = 42;

  @Mock private EnrollmentService enrollmentService;
  @Mock private EnrollmentAdminMapper enrollmentMapper;
  @Mock private AuditLogService auditLogService;
  @Mock private QrCodeGeneratorService qrCodeGeneratorService;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private AccessControlService accessControlService;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private EnrollmentRevocationService enrollmentRevocationService;
  @Mock private EnrollmentUpdateService enrollmentUpdateService;
  @Mock private Authentication authentication;

  private EnrollmentController enrollmentController;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(authentication);
    enrollmentController =
        new EnrollmentController(
            enrollmentService,
            enrollmentMapper,
            auditLogService,
            qrCodeGeneratorService,
            qrCodePayloadService,
            accessControlService,
            enrollmentRepository,
            integrationRepository,
            adminRepository,
            enrollmentRevocationService,
            enrollmentUpdateService,
            new AuditEntityFkResolver(integrationRepository, enrollmentRepository));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Foreign Tenant Admin QR fetch returns 404 and does not load the enrollment")
  void getQrCode_whenAccessDenied_returns404WithoutLoadingEnrollment() {
    when(accessControlService.canAccessEnrollment(authentication, ENROLLMENT_ID)).thenReturn(false);

    ResponseEntity<byte[]> response = enrollmentController.getQrCode(ENROLLMENT_ID);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    verify(enrollmentService, never()).getById(any());
  }

  @Test
  @DisplayName("Owning Tenant Admin QR fetch returns PNG")
  void getQrCode_whenAccessAllowed_returnsPng() throws Exception {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(ENROLLMENT_ID);
    enrollment.setEnrollmentProofToken("proof-token");
    byte[] png = new byte[] {1, 2, 3};

    when(accessControlService.canAccessEnrollment(authentication, ENROLLMENT_ID)).thenReturn(true);
    when(enrollmentService.getById(ENROLLMENT_ID)).thenReturn(enrollment);
    when(qrCodePayloadService.composePayload(ENROLLMENT_ID, "proof-token")).thenReturn("{}");
    when(qrCodeGeneratorService.generateQrCodeImage("{}", 300, 300)).thenReturn(png);

    ResponseEntity<byte[]> response = enrollmentController.getQrCode(ENROLLMENT_ID);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertArrayEquals(png, response.getBody());
    assertEquals("image/png", response.getHeaders().getFirst("Content-Type"));
  }
}
