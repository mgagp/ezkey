/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AlertControllerTest
 * Description: Unit tests for AlertController list and getById endpoints.
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.dto.AlertResponseDto;
import org.ezkey.alert.mapper.AlertMapper;
import org.ezkey.alert.service.AlertService;
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

/**
 * Unit tests for {@link AlertController}.
 *
 * <p>Verifies the read-only endpoints behave correctly (list passes filters; detail returns
 * 200/404). Role-based access (Global Admin only) is enforced via {@code @PreAuthorize} and
 * exercised by the security integration tests.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

  @Mock private AlertService alertService;
  @Mock private AlertMapper alertMapper;

  private AlertController controller;

  @BeforeEach
  void setUp() {
    controller = new AlertController(alertService, alertMapper);
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
}
