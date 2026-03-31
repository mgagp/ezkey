/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: PublicInstanceInfoController
 * Description: Unauthenticated REST endpoint for public instance metadata.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ezkey.admin.config.OrganizationProperties;
import org.ezkey.admin.config.QrCodeProperties;
import org.ezkey.admin.dto.response.PublicInstanceInfoResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes read-only instance metadata without authentication (login page, tooling).
 *
 * <p>Does not replace {@code authUrl} inside scanned enrollment QR payloads; it mirrors {@link
 * QrCodeProperties#getAuthBaseUrl()} for visibility.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public", description = "Unauthenticated instance metadata")
public class PublicInstanceInfoController {

  private final QrCodeProperties qrCodeProperties;
  private final OrganizationProperties organizationProperties;

  public PublicInstanceInfoController(
      QrCodeProperties qrCodeProperties, OrganizationProperties organizationProperties) {
    this.qrCodeProperties = qrCodeProperties;
    this.organizationProperties = organizationProperties;
  }

  /**
   * Returns public instance information (branding, optional public Auth API URL for QR alignment).
   *
   * @return instance metadata
   */
  @Operation(
      summary = "Get public instance info",
      description =
          "Returns read-only instance metadata for the Admin UI (login shell) and operators."
              + " authApiPublicBaseUrl matches the authUrl embedded in enrollment QR codes when"
              + " ezkey.qr.auth-base-url is set.")
  @ApiResponse(
      responseCode = "200",
      description = "Instance metadata",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = PublicInstanceInfoResponseDto.class)))
  @GetMapping("/instance-info")
  public ResponseEntity<PublicInstanceInfoResponseDto> getInstanceInfo() {
    String authBase = qrCodeProperties.getAuthBaseUrl();
    if (authBase != null) {
      authBase = authBase.isBlank() ? null : authBase.strip();
    }

    String aboutUrl = organizationProperties.getAboutUrl();
    if (aboutUrl != null) {
      aboutUrl = aboutUrl.isBlank() ? null : aboutUrl.strip();
    }

    PublicInstanceInfoResponseDto body =
        new PublicInstanceInfoResponseDto(
            authBase,
            organizationProperties.getName(),
            organizationProperties.getDescription(),
            aboutUrl);
    return ResponseEntity.ok(body);
  }
}
