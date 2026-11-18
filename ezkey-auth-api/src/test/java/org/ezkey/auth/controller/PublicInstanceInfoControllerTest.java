/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PublicInstanceInfoControllerTest
 * Description: Unit tests for Auth API public instance-info endpoint.
 */

package org.ezkey.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  private PublicInstanceInfoController controller;

  @BeforeEach
  void setUp() {
    controller = new PublicInstanceInfoController(publicInstanceInfoService);
  }

  @Test
  @DisplayName("getInstanceInfo delegates to PublicInstanceInfoService")
  void getInstanceInfo_delegates() {
    PublicInstanceInfoResponseDto dto =
        new PublicInstanceInfoResponseDto(
            "https://auth.example.com:8080", "Acme", "Desc", "https://about.example.com");
    when(publicInstanceInfoService.getPublicInstanceInfo()).thenReturn(dto);

    ResponseEntity<PublicInstanceInfoResponseDto> response = controller.getInstanceInfo();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(dto, response.getBody());
    verify(publicInstanceInfoService).getPublicInstanceInfo();
  }
}
