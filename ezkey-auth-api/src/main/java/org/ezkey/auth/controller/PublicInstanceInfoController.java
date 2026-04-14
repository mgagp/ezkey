/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: PublicInstanceInfoController
 * Description: Unauthenticated REST endpoint for public instance metadata (mobile and tooling).
 */

package org.ezkey.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.ezkey.instance.service.PublicInstanceInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes read-only instance metadata without authentication (mobile clients, operators).
 *
 * <p>Does not replace {@code authUrl} inside scanned enrollment QR payloads; it mirrors {@link
 * org.ezkey.config.QrCodeProperties#getAuthBaseUrl()} for visibility. Same JSON contract as the
 * Admin API {@code GET /api/v1/public/instance-info}.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public", description = "Unauthenticated instance metadata")
public class PublicInstanceInfoController {

  /** Full path for OpenAPI and documentation. */
  public static final String FULL_PATH_INSTANCE_INFO = "/api/v1/public/instance-info";

  private final PublicInstanceInfoService publicInstanceInfoService;

  public PublicInstanceInfoController(PublicInstanceInfoService publicInstanceInfoService) {
    this.publicInstanceInfoService = publicInstanceInfoService;
  }

  /**
   * Returns public instance information (branding, optional public Auth API URL for QR alignment).
   *
   * @return instance metadata
   */
  @Operation(
      summary = "Get public instance info",
      description =
          "Returns read-only instance metadata for mobile clients and operators. Same payload as"
              + " the Admin API public instance-info. authApiPublicBaseUrl matches the authUrl"
              + " embedded in enrollment QR codes when ezkey.qr.auth-base-url is set.",
      security = {})
  @ApiResponse(
      responseCode = "200",
      description = "Instance metadata",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = PublicInstanceInfoResponseDto.class)))
  @GetMapping("/instance-info")
  public ResponseEntity<PublicInstanceInfoResponseDto> getInstanceInfo() {
    return ResponseEntity.ok(publicInstanceInfoService.getPublicInstanceInfo());
  }
}
