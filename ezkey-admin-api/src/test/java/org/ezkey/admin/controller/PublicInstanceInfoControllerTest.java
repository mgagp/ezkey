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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.ezkey.admin.config.OrganizationProperties;
import org.ezkey.admin.config.QrCodeProperties;
import org.ezkey.admin.dto.response.PublicInstanceInfoResponseDto;
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

  @Mock private QrCodeProperties qrCodeProperties;

  @Mock private OrganizationProperties organizationProperties;

  private PublicInstanceInfoController controller;

  @BeforeEach
  void setUp() {
    controller = new PublicInstanceInfoController(qrCodeProperties, organizationProperties);
  }

  @Test
  @DisplayName("getInstanceInfo returns auth URL, org fields, and about URL")
  void getInstanceInfo_full() {
    when(qrCodeProperties.getAuthBaseUrl()).thenReturn(" https://auth.example.com:8080 ");
    when(organizationProperties.getName()).thenReturn("Acme Corp");
    when(organizationProperties.getDescription()).thenReturn("MFA instance");
    when(organizationProperties.getAboutUrl()).thenReturn(" https://about.example.com ");

    ResponseEntity<PublicInstanceInfoResponseDto> response = controller.getInstanceInfo();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    PublicInstanceInfoResponseDto body = response.getBody();
    assertEquals("https://auth.example.com:8080", body.authApiPublicBaseUrl());
    assertEquals("Acme Corp", body.instanceName());
    assertEquals("MFA instance", body.instanceDescription());
    assertEquals("https://about.example.com", body.aboutUrl());
  }

  @Test
  @DisplayName("getInstanceInfo strips blank auth and about to null")
  void getInstanceInfo_blanksBecomeNull() {
    when(qrCodeProperties.getAuthBaseUrl()).thenReturn("   ");
    when(organizationProperties.getName()).thenReturn("Ezkey System");
    when(organizationProperties.getDescription()).thenReturn("Desc");
    when(organizationProperties.getAboutUrl()).thenReturn("");

    ResponseEntity<PublicInstanceInfoResponseDto> response = controller.getInstanceInfo();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    PublicInstanceInfoResponseDto body = response.getBody();
    assertNull(body.authApiPublicBaseUrl());
    assertNull(body.aboutUrl());
  }
}
