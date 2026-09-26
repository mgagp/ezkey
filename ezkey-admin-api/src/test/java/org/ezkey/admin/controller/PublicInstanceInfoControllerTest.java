/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PublicInstanceInfoControllerTest
 * Description: Unit tests for public instance-info endpoint.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.ezkey.instance.service.PublicInstanceInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Unit tests for {@link PublicInstanceInfoController}. */
@ExtendWith(MockitoExtension.class)
class PublicInstanceInfoControllerTest {

  @Mock private PublicInstanceInfoService publicInstanceInfoService;
  @Mock private EvaluatorSelfRegistrationProperties evaluatorSelfRegistrationProperties;

  private PublicInstanceInfoController controller;

  @BeforeEach
  void setUp() {
    controller =
        new PublicInstanceInfoController(
            publicInstanceInfoService, evaluatorSelfRegistrationProperties);
  }

  @Test
  @DisplayName("getInstanceInfo enriches branding with evaluator self-registration flag")
  void getInstanceInfo_enrichesWithSelfRegFlag() {
    PublicInstanceInfoResponseDto base =
        new PublicInstanceInfoResponseDto(
            "https://auth.example.com:8080", "Acme", "Desc", "https://about.example.com", null);
    when(publicInstanceInfoService.getPublicInstanceInfo()).thenReturn(base);
    when(evaluatorSelfRegistrationProperties.isEnabled()).thenReturn(true);

    ResponseEntity<PublicInstanceInfoResponseDto> response = controller.getInstanceInfo();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    PublicInstanceInfoResponseDto body = response.getBody();
    assertEquals("Acme", body.instanceName());
    assertTrue(Boolean.TRUE.equals(body.evaluatorSelfRegistrationEnabled()));
    verify(publicInstanceInfoService).getPublicInstanceInfo();
  }
}
