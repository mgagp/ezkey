/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PublicInstanceInfoServiceTest
 * Description: Unit tests for {@link PublicInstanceInfoService}.
 */

package org.ezkey.instance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.ezkey.config.OrganizationProperties;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link PublicInstanceInfoService}. */
@ExtendWith(MockitoExtension.class)
class PublicInstanceInfoServiceTest {

  @Mock private QrCodeProperties qrCodeProperties;

  @Mock private OrganizationProperties organizationProperties;

  private PublicInstanceInfoService service;

  @BeforeEach
  void setUp() {
    service = new PublicInstanceInfoService(qrCodeProperties, organizationProperties);
  }

  @Test
  @DisplayName("getPublicInstanceInfo returns auth URL, org fields, and about URL")
  void getPublicInstanceInfo_full() {
    when(qrCodeProperties.getAuthBaseUrl()).thenReturn(" https://auth.example.com:8080 ");
    when(organizationProperties.getName()).thenReturn("Acme Corp");
    when(organizationProperties.getDescription()).thenReturn("MFA instance");
    when(organizationProperties.getAboutUrl()).thenReturn(" https://about.example.com ");

    PublicInstanceInfoResponseDto body = service.getPublicInstanceInfo();

    assertEquals("https://auth.example.com:8080", body.authApiPublicBaseUrl());
    assertEquals("Acme Corp", body.instanceName());
    assertEquals("MFA instance", body.instanceDescription());
    assertEquals("https://about.example.com", body.aboutUrl());
  }

  @Test
  @DisplayName("getPublicInstanceInfo strips blank auth and about to null")
  void getPublicInstanceInfo_blanksBecomeNull() {
    when(qrCodeProperties.getAuthBaseUrl()).thenReturn("   ");
    when(organizationProperties.getName()).thenReturn("Ezkey System");
    when(organizationProperties.getDescription()).thenReturn("Desc");
    when(organizationProperties.getAboutUrl()).thenReturn("");

    PublicInstanceInfoResponseDto body = service.getPublicInstanceInfo();

    assertNull(body.authApiPublicBaseUrl());
    assertNull(body.aboutUrl());
  }
}
