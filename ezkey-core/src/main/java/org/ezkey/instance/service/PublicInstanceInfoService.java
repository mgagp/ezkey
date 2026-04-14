/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: PublicInstanceInfoService
 * Description: Builds public instance metadata for Admin API and Auth API instance-info endpoints.
 */

package org.ezkey.instance.service;

import org.ezkey.config.OrganizationProperties;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.springframework.stereotype.Service;

/**
 * Builds {@link PublicInstanceInfoResponseDto} from {@link QrCodeProperties} and {@link
 * OrganizationProperties}.
 */
@Service
public class PublicInstanceInfoService {

  private final QrCodeProperties qrCodeProperties;

  private final OrganizationProperties organizationProperties;

  public PublicInstanceInfoService(
      QrCodeProperties qrCodeProperties, OrganizationProperties organizationProperties) {
    this.qrCodeProperties = qrCodeProperties;
    this.organizationProperties = organizationProperties;
  }

  /**
   * Returns public instance information (branding, optional public Auth API URL for QR alignment).
   *
   * @return instance metadata
   */
  public PublicInstanceInfoResponseDto getPublicInstanceInfo() {
    String authBase = qrCodeProperties.getAuthBaseUrl();
    if (authBase != null) {
      authBase = authBase.isBlank() ? null : authBase.strip();
    }

    String aboutUrl = organizationProperties.getAboutUrl();
    if (aboutUrl != null) {
      aboutUrl = aboutUrl.isBlank() ? null : aboutUrl.strip();
    }

    return new PublicInstanceInfoResponseDto(
        authBase,
        organizationProperties.getName(),
        organizationProperties.getDescription(),
        aboutUrl);
  }
}
