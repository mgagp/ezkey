/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.ezkey.instance.service.PublicInstanceInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes read-only instance metadata without authentication (login page, tooling).
 *
 * <p>Does not replace {@code authUrl} inside scanned enrollment QR payloads; it mirrors {@link
 * org.ezkey.config.QrCodeProperties#getAuthBaseUrl()} for visibility.
 *
 * <p>Enriches the shared DTO with {@code evaluatorSelfRegistrationEnabled} so the Admin UI can
 * advertise the temporary console explore fork only when the self-registration gate is ON.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(
    name = "Public",
    description =
        "Unauthenticated instance metadata, evaluator preview signup, and related public endpoints")
public class PublicInstanceInfoController {

  private final PublicInstanceInfoService publicInstanceInfoService;
  private final EvaluatorSelfRegistrationProperties evaluatorSelfRegistrationProperties;

  /**
   * Creates the public instance-info controller.
   *
   * @param publicInstanceInfoService shared branding/metadata service
   * @param evaluatorSelfRegistrationProperties evaluator self-registration gate (same flag as Mode
   *     C temporary console)
   */
  public PublicInstanceInfoController(
      PublicInstanceInfoService publicInstanceInfoService,
      EvaluatorSelfRegistrationProperties evaluatorSelfRegistrationProperties) {
    this.publicInstanceInfoService = publicInstanceInfoService;
    this.evaluatorSelfRegistrationProperties = evaluatorSelfRegistrationProperties;
  }

  /**
   * Returns public instance information (branding, optional public Auth API URL for QR alignment).
   *
   * @return instance metadata including whether evaluator self-registration / temporary explore is
   *     enabled
   */
  @Operation(
      summary = "Get public instance info",
      description =
          "Returns read-only instance metadata for the Admin UI (login shell) and operators."
              + " authApiPublicBaseUrl matches the authUrl embedded in enrollment QR codes when"
              + " ezkey.qr.auth-base-url is set. evaluatorSelfRegistrationEnabled mirrors"
              + " ezkey.evaluator.self-registration.enabled (temporary console explore gate).",
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
    PublicInstanceInfoResponseDto base = publicInstanceInfoService.getPublicInstanceInfo();
    return ResponseEntity.ok(
        new PublicInstanceInfoResponseDto(
            base.authApiPublicBaseUrl(),
            base.instanceName(),
            base.instanceDescription(),
            base.aboutUrl(),
            evaluatorSelfRegistrationProperties.isEnabled()));
  }
}
