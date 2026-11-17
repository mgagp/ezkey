/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.dto.request.EnrollmentUpdateRequestDto;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.admin.service.EnrollmentUpdateService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.AdminEnrollmentOperationException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@code /enrollments} on an administrator's MFA enrollment (#1313): the record opens with no bind
 * material, the QR is refused, a contact email write is refused and audited, and a Global Admin
 * gets the back-link to the owning administrator.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentController on an administrator enrollment (#1313)")
class EnrollmentControllerAdminEnrollmentTest {

  private static final Integer ENROLLMENT_ID = 42;
  private static final Integer SYSTEM_INTEGRATION_ID = 1;
  private static final Integer OWNER_ADMIN_ID = 7;

  @Mock private EnrollmentService enrollmentService;
  @Mock private AuditLogService auditLogService;
  @Mock private QrCodeGeneratorService qrCodeGeneratorService;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private AccessControlService accessControlService;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private EnrollmentRevocationService enrollmentRevocationService;
  @Mock private EnrollmentUpdateService enrollmentUpdateService;
  @Mock private HttpServletRequest httpRequest;

  private final EnrollmentAdminMapper enrollmentMapper =
      Mappers.getMapper(EnrollmentAdminMapper.class);

  private EnrollmentController controller;
  private Enrollment adminEnrollment;
  private Integration systemIntegration;

  @BeforeEach
  void setUp() {
    controller =
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

    systemIntegration = new Integration();
    systemIntegration.setId(SYSTEM_INTEGRATION_ID);
    systemIntegration.setIsSystemIntegration(true);

    adminEnrollment = new Enrollment();
    adminEnrollment.setEnrollmentId(ENROLLMENT_ID);
    adminEnrollment.setIntegrationId(SYSTEM_INTEGRATION_ID);
    adminEnrollment.setStatus(EnrollmentStatus.CREATED);
    adminEnrollment.setEnrollmentProofToken("admin-proof-token");
    adminEnrollment.setEnrollmentChallenge(123456);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private Authentication signIn(AdminType type, Integer tenantId) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new AdminPrincipal(1, type, tenantId, null),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
  }

  @Test
  @DisplayName("T8: GET returns the record with null token and challenge, and the owner for Global")
  void getById_adminEnrollment_hidesBindMaterialAndLinksOwner() {
    Authentication auth = signIn(AdminType.GLOBAL_ADMIN, null);
    EzkeyAdmin owner = new EzkeyAdmin();
    owner.setAdminId(OWNER_ADMIN_ID);
    when(accessControlService.canAccessEnrollment(auth, ENROLLMENT_ID)).thenReturn(true);
    when(enrollmentService.getById(ENROLLMENT_ID)).thenReturn(adminEnrollment);
    when(integrationRepository.findById(SYSTEM_INTEGRATION_ID))
        .thenReturn(Optional.of(systemIntegration));
    when(adminRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.of(owner));

    ResponseEntity<EnrollmentResponseDto> response = controller.getById(ENROLLMENT_ID);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    EnrollmentResponseDto body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.enrollmentProofToken()).isNull();
    assertThat(body.enrollmentChallenge()).isNull();
    assertThat(body.isSystemIntegration()).isTrue();
    assertThat(body.ownerAdminId()).isEqualTo(OWNER_ADMIN_ID);
  }

  @Test
  @DisplayName("An enrollment no administrator owns opens with no owner")
  void getById_ownerlessAdminEnrollment_hasNoOwner() {
    Authentication auth = signIn(AdminType.GLOBAL_ADMIN, null);
    when(accessControlService.canAccessEnrollment(auth, ENROLLMENT_ID)).thenReturn(true);
    when(enrollmentService.getById(ENROLLMENT_ID)).thenReturn(adminEnrollment);
    when(integrationRepository.findById(SYSTEM_INTEGRATION_ID))
        .thenReturn(Optional.of(systemIntegration));
    when(adminRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());

    ResponseEntity<EnrollmentResponseDto> response = controller.getById(ENROLLMENT_ID);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().ownerAdminId()).isNull();
  }

  @Test
  @DisplayName("A caller that is not Global never gets the owner, even if access were granted")
  void getById_tenantAdmin_neverGetsOwner() {
    Authentication auth = signIn(AdminType.TENANT_ADMIN, 5);
    when(accessControlService.canAccessEnrollment(auth, ENROLLMENT_ID)).thenReturn(true);
    when(enrollmentService.getById(ENROLLMENT_ID)).thenReturn(adminEnrollment);
    when(integrationRepository.findById(SYSTEM_INTEGRATION_ID))
        .thenReturn(Optional.of(systemIntegration));

    ResponseEntity<EnrollmentResponseDto> response = controller.getById(ENROLLMENT_ID);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().ownerAdminId()).isNull();
    verify(adminRepository, never()).findByEnrollmentId(any());
  }

  @Test
  @DisplayName("Ordinary enrollments keep their bind material and have no owner")
  void getById_ordinaryEnrollment_keepsBindMaterial() {
    Authentication auth = signIn(AdminType.GLOBAL_ADMIN, null);
    Integration ordinary = new Integration();
    ordinary.setId(2);
    ordinary.setIsSystemIntegration(false);
    adminEnrollment.setIntegrationId(2);
    when(accessControlService.canAccessEnrollment(auth, ENROLLMENT_ID)).thenReturn(true);
    when(enrollmentService.getById(ENROLLMENT_ID)).thenReturn(adminEnrollment);
    when(integrationRepository.findById(2)).thenReturn(Optional.of(ordinary));

    ResponseEntity<EnrollmentResponseDto> response = controller.getById(ENROLLMENT_ID);

    EnrollmentResponseDto body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.enrollmentProofToken()).isEqualTo("admin-proof-token");
    assertThat(body.enrollmentChallenge()).isEqualTo(123456);
    assertThat(body.ownerAdminId()).isNull();
    verify(adminRepository, never()).findByEnrollmentId(any());
  }

  @Test
  @DisplayName("T8: the QR of an admin enrollment is refused with admin-enrollment, never composed")
  void getQrCode_adminEnrollment_isRefused() throws Exception {
    Authentication auth = signIn(AdminType.GLOBAL_ADMIN, null);
    when(accessControlService.canAccessEnrollment(auth, ENROLLMENT_ID)).thenReturn(true);
    when(enrollmentService.getById(ENROLLMENT_ID)).thenReturn(adminEnrollment);
    when(integrationRepository.findById(SYSTEM_INTEGRATION_ID))
        .thenReturn(Optional.of(systemIntegration));

    assertThatThrownBy(() -> controller.getQrCode(ENROLLMENT_ID))
        .isInstanceOf(AdminEnrollmentOperationException.class);
    verify(qrCodePayloadService, never()).composePayload(any());
    verify(qrCodeGeneratorService, never()).generateQrCodeImage(any(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("T1: a refused contactEmail write is audited as a failed update, then propagated")
  void update_adminEnrollmentContactEmail_isAuditedAndPropagated() {
    Authentication auth = signIn(AdminType.GLOBAL_ADMIN, null);
    EnrollmentUpdateRequestDto request =
        new EnrollmentUpdateRequestDto(
            0L, null, "someone@example.com", null, null, null, null, null, null);
    when(accessControlService.canAccessEnrollment(auth, ENROLLMENT_ID)).thenReturn(true);
    when(enrollmentUpdateService.updateEnrollment(eq(ENROLLMENT_ID), any()))
        .thenThrow(new AdminEnrollmentOperationException("refused"));

    assertThatThrownBy(() -> controller.update(ENROLLMENT_ID, request, httpRequest))
        .isInstanceOf(AdminEnrollmentOperationException.class);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(captor.capture());
    AuditLog row = captor.getValue();
    assertThat(row.getEventType()).isEqualTo(EventType.ENROLLMENT_UPDATED);
    assertThat(row.getEventStatus()).isEqualTo(EventStatus.FAILURE);
    assertThat(row.getErrorMessage()).isEqualTo(AdminEnrollmentOperationException.CODE);
  }
}
