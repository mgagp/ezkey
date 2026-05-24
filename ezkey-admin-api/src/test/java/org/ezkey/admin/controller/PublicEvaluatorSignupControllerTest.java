/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PublicEvaluatorSignupControllerTest
 * Description: Unit tests for public evaluator signup endpoint.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.ezkey.admin.dto.request.EvaluatorSelfRegistrationRequestDto;
import org.ezkey.admin.dto.response.EvaluatorSelfRegistrationResponseDto;
import org.ezkey.admin.service.EvaluatorSelfRegistrationService;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicEvaluatorSignupController")
class PublicEvaluatorSignupControllerTest {

  @Mock private EvaluatorSelfRegistrationService evaluatorSelfRegistrationService;
  @Mock private AuditLogService auditLogService;
  @Mock private HttpServletRequest httpRequest;

  private PublicEvaluatorSignupController controller;

  @BeforeEach
  void setUp() {
    controller =
        new PublicEvaluatorSignupController(evaluatorSelfRegistrationService, auditLogService);
  }

  @Test
  @DisplayName("returns 404 when feature disabled")
  void disabled_returns404() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(false);

    ResponseEntity<EvaluatorSelfRegistrationResponseDto> response =
        controller.evaluatorSignup(new EvaluatorSelfRegistrationRequestDto("Lab"), httpRequest);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    verify(evaluatorSelfRegistrationService, never()).register(any(), any());
  }

  @Test
  @DisplayName("returns 201 with activation payload when enabled")
  void enabled_returns201() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(true);
    when(httpRequest.getAttribute(org.ezkey.audit.util.ClientContext.CLIENT_IP_REQUEST_ATTRIBUTE))
        .thenReturn("203.0.113.8");
    EvaluatorSelfRegistrationResponseDto body =
        new EvaluatorSelfRegistrationResponseDto(
            "WXYZ-5678",
            OffsetDateTime.now().plusDays(7),
            "https://exp1-admin-ui.ezkey.org",
            "https://ezkey.org/exp1-guided-tour.html",
            "eval-cafebabe");
    when(evaluatorSelfRegistrationService.register(eq("Lab"), eq("203.0.113.8"))).thenReturn(body);

    ResponseEntity<EvaluatorSelfRegistrationResponseDto> response =
        controller.evaluatorSignup(new EvaluatorSelfRegistrationRequestDto("Lab"), httpRequest);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(body, response.getBody());
    verify(auditLogService).log(any());
  }
}
