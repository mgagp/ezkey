/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AdminLoginResponseDto
 * Description: Response DTO for passwordless administrator login.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;

/**
 * Response DTO for passwordless administrator login.
 *
 * <p>This record contains the authentication response including the bearer token and administrator
 * information after successful passwordless login.
 *
 * <p>Supports two authentication flows:
 *
 * <ul>
 *   <li><b>Single-call (no challenge):</b> Returns bearer token immediately after device approval
 *   <li><b>Two-call (with challenge):</b> Returns authAttemptId and challengeCode, requires
 *       /passwordless-wait call
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Response DTO for passwordless administrator login")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminLoginResponseDto(
    @Schema(description = "Indicates if the authentication was successful", example = "true")
        Boolean success,
    @Schema(
            description = "Response message describing authentication result",
            example = "Authentication successful")
        String message,
    @Schema(
            description = "Authentication attempt status",
            example = "pending",
            allowableValues = {"pending", "accepted", "rejected"})
        String status,
    @Schema(
            description =
                "Bearer token for authenticated API requests. Omitted when the API issues an"
                    + " HttpOnly session cookie (browser split UI/API).",
            example = "ezkey_abc123def456...",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String token,
    @Schema(
            description = "Type of administrator",
            example = "GLOBAL_ADMIN",
            allowableValues = {"GLOBAL_ADMIN", "TENANT_ADMIN", "INTEGRATION_ADMIN"})
        String adminType,
    @Schema(description = "Administrator username", example = "admin") String username,
    @Schema(
            description =
                "Expiration timestamp (UTC). When authentication succeeded: bearer token expiry."
                    + " When status is pending (two-step passwordless): authentication attempt"
                    + " expiry — matches the persisted attempt and core setting"
                    + " ezkey.core.auth-attempt.ttl-seconds (not a fixed duration).",
            example = "2025-10-15T14:30:00+01:00")
        OffsetDateTime expiresAt,
    @Schema(description = "Authentication attempt ID for two-step flow", example = "123")
        Integer authAttemptId,
    @Schema(description = "6-digit challenge code for device verification", example = "654321")
        Integer challengeCode,
    @Schema(description = "Administrator ID for the authenticated session", example = "2")
        Integer adminId,
    @Schema(
            description =
                "Tenant scope ID when the administrator is tenant- or integration-scoped; null for"
                    + " global administrators",
            example = "3")
        Integer tenantId) {

  /**
   * Constructor for error responses.
   *
   * @param message the error message
   */
  public AdminLoginResponseDto(String message) {
    this(false, message, null, null, null, null, null, null, null, null, null);
  }

  /**
   * Constructor for successful token-based authentication.
   *
   * @param token the bearer token
   * @param adminType the administrator type
   * @param username the administrator username
   * @param expiresAt the token expiration time
   * @param adminId the administrator ID
   * @param tenantId tenant scope when applicable, otherwise null
   */
  public AdminLoginResponseDto(
      String token,
      String adminType,
      String username,
      OffsetDateTime expiresAt,
      Integer adminId,
      Integer tenantId) {
    this(
        true,
        "Authentication successful",
        "approved",
        token,
        adminType,
        username,
        expiresAt,
        null,
        null,
        adminId,
        tenantId);
  }

  /**
   * Factory method for passwordless pending response (two-step flow).
   *
   * @param authAttemptId the authentication attempt ID
   * @param challengeCode the 6-digit challenge code
   * @param username the administrator username
   * @param message the instruction message
   * @param expiresAt when the auth attempt expires
   * @return a pending passwordless response
   */
  public static AdminLoginResponseDto pendingPasswordless(
      Integer authAttemptId,
      Integer challengeCode,
      String username,
      String adminType,
      String message,
      OffsetDateTime expiresAt) {
    return new AdminLoginResponseDto(
        false,
        message,
        "pending",
        null,
        adminType,
        username,
        expiresAt,
        authAttemptId,
        challengeCode,
        null,
        null);
  }

  /**
   * Factory method for success response.
   *
   * @param token the bearer token
   * @param adminType the administrator type
   * @param username the administrator username
   * @param expiresAt the token expiration time
   * @param adminId the administrator ID
   * @param tenantId tenant scope when applicable
   * @return a success response
   */
  public static AdminLoginResponseDto success(
      String token,
      String adminType,
      String username,
      OffsetDateTime expiresAt,
      Integer adminId,
      Integer tenantId) {
    return new AdminLoginResponseDto(
        true,
        "Authentication successful",
        "approved",
        token,
        adminType,
        username,
        expiresAt,
        null,
        null,
        adminId,
        tenantId);
  }

  /**
   * Factory method for error response.
   *
   * @param message the error message
   * @return an error response
   */
  public static AdminLoginResponseDto error(String message) {
    return new AdminLoginResponseDto(
        false, message, null, null, null, null, null, null, null, null, null);
  }

  /**
   * Same payload as this response but with {@link #token} removed for JSON serialization when the
   * opaque value was already sent as an HttpOnly cookie.
   *
   * @return a copy with {@code token == null}, or {@code this} if there was no token
   */
  public AdminLoginResponseDto withoutSecretToken() {
    if (token == null) {
      return this;
    }
    return new AdminLoginResponseDto(
        success,
        message,
        status,
        null,
        adminType,
        username,
        expiresAt,
        authAttemptId,
        challengeCode,
        adminId,
        tenantId);
  }

  @Override
  public String toString() {
    return "AdminLoginResponseDto[success=%s, status=%s, username=%s, hasToken=%s]"
        .formatted(success, status, username, token != null ? "yes" : "no");
  }
}
