/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AlertControllerTest
 * Description: Unit tests for AlertController list, detail, and manual resolve endpoints.
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.ezkey.admin.dto.request.AlertResolveRequestDto;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.dto.AlertResponseDto;
import org.ezkey.alert.mapper.AlertMapper;
import org.ezkey.alert.service.AlertService;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link AlertController}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

  @Mock private AlertService alertService;
  @Mock private AlertMapper alertMapper;
  @Mock private AccessControlService accessControlService;

  private AlertController controller;

  @BeforeEach
  void setUp() {
    controller = new AlertController(alertService, alertMapper, accessControlService);
  }

  @Test
  void listAlerts_returnsPagedDtos() {
    Alert entity = new Alert();
    entity.setAlertId(1L);
    entity.setAlertType(AlertType.AUDIT_CHAIN_GAP_PENDING);
    entity.setSeverity(AlertSeverity.WARNING);
    entity.setStatus(AlertStatus.OPEN);

    AlertResponseDto dto = new AlertResponseDto();
    dto.setAlertId(1L);
    dto.setAlertType(AlertType.AUDIT_CHAIN_GAP_PENDING);
    dto.setSeverity(AlertSeverity.WARNING);
    dto.setStatus(AlertStatus.OPEN);

    Pageable pageable = PageRequest.of(0, 20);
    Page<Alert> page = new PageImpl<>(List.of(entity), pageable, 1);

    when(alertService.search(
            eq(AlertStatus.OPEN), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
        .thenReturn(page);
    when(alertMapper.toResponseDto(entity)).thenReturn(dto);

    ResponseEntity<Page<AlertResponseDto>> response =
        controller.listAlerts(AlertStatus.OPEN, null, null, null, null, null, pageable);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent()).containsExactly(dto);
  }

  @Test
  void getAlert_whenFound_returns200() {
    Alert entity = new Alert();
    entity.setAlertId(42L);
    AlertResponseDto dto = new AlertResponseDto();
    dto.setAlertId(42L);

    when(alertService.findById(42L)).thenReturn(Optional.of(entity));
    when(alertMapper.toResponseDto(entity)).thenReturn(dto);

    ResponseEntity<AlertResponseDto> response = controller.getAlert(42L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(dto);
  }

  @Test
  void getAlert_whenMissing_returns404() {
    when(alertService.findById(999L)).thenReturn(Optional.empty());

    ResponseEntity<AlertResponseDto> response = controller.getAlert(999L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void resolveAlert_whenOpenManualType_returns200() {
    Alert entity = new Alert();
    entity.setAlertId(101L);
    entity.setAlertType(AlertType.AUTH_ATTEMPT_REJECTED);
    entity.setStatus(AlertStatus.OPEN);
    entity.setDedupeKey("AUTH_ATTEMPT_REJECTED:7");

    Alert resolved = new Alert();
    resolved.setAlertId(101L);
    resolved.setAlertType(AlertType.AUTH_ATTEMPT_REJECTED);
    resolved.setStatus(AlertStatus.RESOLVED);

    AlertResponseDto dto = new AlertResponseDto();
    dto.setAlertId(101L);
    dto.setStatus(AlertStatus.RESOLVED);
    dto.setResolutionReason(AlertResolutionReason.MANUAL);

    Authentication authentication = globalAdminAuth(12);

    when(alertService.findById(101L)).thenReturn(Optional.of(entity));
    when(alertService.resolveOpenById(
            101L, AlertResolutionReason.MANUAL, 12, "Handled by operator"))
        .thenReturn(Optional.of(resolved));
    when(alertMapper.toResponseDto(resolved)).thenReturn(dto);

    ResponseEntity<AlertResponseDto> response =
        controller.resolveAlert(
            101L, new AlertResolveRequestDto("  Handled by operator  "), authentication);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(dto);
  }

  @Test
  void resolveAlert_whenAutomaticType_returns409() {
    Alert entity = new Alert();
    entity.setAlertId(102L);
    entity.setAlertType(AlertType.AUDIT_CHAIN_GAP_PENDING);
    entity.setStatus(AlertStatus.OPEN);

    when(alertService.findById(102L)).thenReturn(Optional.of(entity));

    ResponseEntity<AlertResponseDto> response =
        controller.resolveAlert(102L, new AlertResolveRequestDto("ignored"), globalAdminAuth(12));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verify(alertService, never()).resolveOpenById(any(), any(), any(), any());
  }

  @Test
  void resolveAlert_whenAlreadyResolved_returns409() {
    Alert entity = new Alert();
    entity.setAlertId(103L);
    entity.setAlertType(AlertType.AUTH_ATTEMPT_REJECTED);
    entity.setStatus(AlertStatus.RESOLVED);

    when(alertService.findById(103L)).thenReturn(Optional.of(entity));

    ResponseEntity<AlertResponseDto> response =
        controller.resolveAlert(103L, null, globalAdminAuth(12));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verify(alertService, never()).resolveOpenById(any(), any(), any(), any());
  }

  @Test
  void resolveAlert_whenMissing_returns404() {
    when(alertService.findById(404L)).thenReturn(Optional.empty());

    ResponseEntity<AlertResponseDto> response =
        controller.resolveAlert(404L, null, globalAdminAuth(12));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void resolveAlert_whenTenantCannotAccessEnrollment_returns404() {
    Alert entity = new Alert();
    entity.setAlertId(104L);
    entity.setAlertType(AlertType.AUTH_ATTEMPT_REJECTED);
    entity.setStatus(AlertStatus.OPEN);
    entity.setDedupeKey("AUTH_ATTEMPT_REJECTED:77");

    Authentication authentication = tenantAdminAuth(22, 3);
    when(alertService.findById(104L)).thenReturn(Optional.of(entity));
    when(accessControlService.canAccessEnrollment(authentication, 77)).thenReturn(false);

    ResponseEntity<AlertResponseDto> response =
        controller.resolveAlert(104L, new AlertResolveRequestDto("note"), authentication);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(alertService, never()).resolveOpenById(any(), any(), any(), any());
  }

  private static Authentication globalAdminAuth(int adminId) {
    AdminPrincipal principal = new AdminPrincipal(adminId, AdminType.GLOBAL_ADMIN, null, null);
    return new TestingAuthenticationToken(principal, null, "ROLE_ADMIN", "ROLE_GLOBAL_ADMIN");
  }

  private static Authentication tenantAdminAuth(int adminId, int tenantId) {
    AdminPrincipal principal = new AdminPrincipal(adminId, AdminType.TENANT_ADMIN, tenantId, null);
    return new TestingAuthenticationToken(principal, null, "ROLE_ADMIN", "ROLE_TENANT_ADMIN");
  }
}
