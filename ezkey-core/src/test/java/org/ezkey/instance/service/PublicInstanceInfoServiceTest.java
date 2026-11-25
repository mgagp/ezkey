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

import java.util.Properties;
import org.ezkey.config.OrganizationProperties;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

/** Unit tests for {@link PublicInstanceInfoService}. */
@ExtendWith(MockitoExtension.class)
class PublicInstanceInfoServiceTest {

  @Mock private QrCodeProperties qrCodeProperties;

  @Mock private OrganizationProperties organizationProperties;

  @Mock private ObjectProvider<BuildProperties> buildProperties;

  private PublicInstanceInfoService service;

  @BeforeEach
  void setUp() {
    service =
        new PublicInstanceInfoService(
            qrCodeProperties, organizationProperties, buildProperties, "");
  }

  private static BuildProperties buildInfo(String version) {
    Properties entries = new Properties();
    entries.setProperty("version", version);
    return new BuildProperties(entries);
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

  @Test
  @DisplayName("getPublicInstanceInfo exposes product SemVer and a 7-char SHA (#1477)")
  void getPublicInstanceInfo_buildIdentity() {
    when(buildProperties.getIfAvailable()).thenReturn(buildInfo("0.2.0-SNAPSHOT"));
    service =
        new PublicInstanceInfoService(
            qrCodeProperties,
            organizationProperties,
            buildProperties,
            " 91DC5FE0123456789abcdef0123456789abcdef0 ");

    PublicInstanceInfoResponseDto body = service.getPublicInstanceInfo();

    assertEquals("0.2.0-SNAPSHOT", body.productVersion());
    assertEquals("91dc5fe", body.gitShortSha());
  }

  @Test
  @DisplayName("getPublicInstanceInfo leaves build identity null without build-info or revision")
  void getPublicInstanceInfo_noBuildIdentity() {
    PublicInstanceInfoResponseDto body = service.getPublicInstanceInfo();

    assertNull(body.productVersion());
    assertNull(body.gitShortSha());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "unknown", "91dc5f", "91dc5fe; rm -rf /", "g1dc5fe", "v0.2.0"})
  @DisplayName("toShortSha refuses anything that is not a 7 to 64 hex SHA")
  void toShortSha_rejectsNonSha(String revision) {
    assertNull(PublicInstanceInfoService.toShortSha(revision));
  }

  @Test
  @DisplayName("toShortSha accepts a short SHA as is")
  void toShortSha_acceptsShortSha() {
    assertEquals("91dc5fe", PublicInstanceInfoService.toShortSha("91dc5fe"));
  }
}
