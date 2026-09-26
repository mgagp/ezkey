/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EvaluatorTempSessionResponseDto
 * Description: Response after minting a temporary evaluator console session.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;

/**
 * Response after minting a Mode C temporary console session.
 *
 * @param success whether mint succeeded
 * @param message operator-facing message
 * @param token opaque bearer when cookie mode is off (never log)
 * @param adminType always TENANT_ADMIN for this path
 * @param username administrator username
 * @param expiresAt absolute TEMP expiry
 * @param adminId administrator id
 * @param tenantId tenant scope
 * @param tenantName tenant display name
 * @param tokenPurpose always EVALUATOR_TEMP on success
 * @param csrfToken CSRF token when cookie mode is on
 */
@Schema(description = "Temporary evaluator console session mint response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluatorTempSessionResponseDto(
    @Schema(description = "Whether the temporary session was minted", example = "true")
        Boolean success,
    @Schema(description = "Operator-facing message") String message,
    @Schema(
            description =
                "Opaque bearer token. Omitted when the API issues an HttpOnly session cookie.",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String token,
    @Schema(description = "Administrator type", example = "TENANT_ADMIN") String adminType,
    @Schema(description = "Administrator username") String username,
    @Schema(description = "Absolute temporary session expiry (UTC)") OffsetDateTime expiresAt,
    @Schema(description = "Administrator ID") Integer adminId,
    @Schema(description = "Tenant scope ID") Integer tenantId,
    @Schema(description = "Tenant display name") String tenantName,
    @Schema(description = "Token purpose", example = "EVALUATOR_TEMP") String tokenPurpose,
    @Schema(description = "CSRF token for cookie mode", requiredMode = RequiredMode.NOT_REQUIRED)
        String csrfToken) {

  /**
   * Success factory.
   *
   * @param token opaque bearer
   * @param adminType admin type name
   * @param username username
   * @param expiresAt absolute expiry
   * @param adminId admin id
   * @param tenantId tenant id
   * @param tenantName tenant name
   * @return success response
   */
  public static EvaluatorTempSessionResponseDto success(
      String token,
      String adminType,
      String username,
      OffsetDateTime expiresAt,
      Integer adminId,
      Integer tenantId,
      String tenantName) {
    return new EvaluatorTempSessionResponseDto(
        true,
        "Temporary console session issued",
        token,
        adminType,
        username,
        expiresAt,
        adminId,
        tenantId,
        tenantName,
        "EVALUATOR_TEMP",
        null);
  }

  /**
   * Error factory.
   *
   * @param message error message
   * @return error response
   */
  public static EvaluatorTempSessionResponseDto error(String message) {
    return new EvaluatorTempSessionResponseDto(
        false, message, null, null, null, null, null, null, null, null, null);
  }

  /**
   * Copy without secret token (cookie already set).
   *
   * @return copy with token null
   */
  public EvaluatorTempSessionResponseDto withoutSecretToken() {
    if (token == null) {
      return this;
    }
    return new EvaluatorTempSessionResponseDto(
        success,
        message,
        null,
        adminType,
        username,
        expiresAt,
        adminId,
        tenantId,
        tenantName,
        tokenPurpose,
        csrfToken);
  }

  /**
   * Copy with CSRF token for cookie mode.
   *
   * @param newCsrfToken CSRF token
   * @return copy including CSRF
   */
  public EvaluatorTempSessionResponseDto withCsrfToken(String newCsrfToken) {
    return new EvaluatorTempSessionResponseDto(
        success,
        message,
        token,
        adminType,
        username,
        expiresAt,
        adminId,
        tenantId,
        tenantName,
        tokenPurpose,
        newCsrfToken);
  }
}
