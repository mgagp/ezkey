/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminLoginResponseDto
 * Description: Response DTO for passwordless administrator login.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Response DTO for passwordless administrator login.
 *
 * <p>This DTO contains the authentication response including the bearer token and administrator
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Response DTO for passwordless administrator login")
public class AdminLoginResponseDto {

  /**
   * Indicates if the authentication was successful.
   */
  @Schema(
      description = "Indicates if the authentication was successful",
      example = "true",
      required = true)
  private Boolean success;

  /**
   * Bearer token for subsequent API calls.
   */
  @Schema(
      description = "Bearer token for authenticated API requests",
      example = "ezkey_abc123def456...",
      required = false)
  private String token;

  /**
   * Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN).
   */
  @Schema(
      description = "Type of administrator",
      example = "GLOBAL_ADMIN",
      allowableValues = {"GLOBAL_ADMIN", "TENANT_ADMIN", "INTEGRATION_ADMIN"},
      required = false)
  private String adminType;

  /**
   * Administrator username.
   */
  @Schema(description = "Administrator username", example = "admin", required = false)
  private String username;

  /**
   * Token expiration timestamp.
   */
  @Schema(
      description = "Token expiration timestamp",
      example = "2025-10-15T14:30:00",
      required = false)
  private LocalDateTime expiresAt;

  /**
   * Response message.
   */
  @Schema(
      description = "Response message describing authentication result",
      example = "Authentication successful",
      required = true)
  private String message;

  /**
   * Authentication attempt ID for passwordless two-step flow.
   *
   * <p>Present when passwordless login is initiated with challengeRequested=true. The client must
   * use this ID along with the challengeCode to call /passwordless-wait endpoint.
   */
  @Schema(
      description =
          "Authentication attempt ID for two-step flow (present when challengeRequested=true)",
      example = "123",
      required = false)
  private Integer authAttemptId;

  /**
   * Challenge code for passwordless two-step flow.
   *
   * <p>A 6-digit code that the user must enter on their device during approval. Also serves as
   * proof of legitimate authentication initiation when calling /passwordless-wait (anti-enumeration
   * protection).
   */
  @Schema(
      description =
          "6-digit challenge code for device verification (present when challengeRequested=true)",
      example = "654321",
      required = false)
  private Integer challengeCode;

  /**
   * Status of the authentication attempt.
   *
   * <p>Used in passwordless two-step flow to indicate authentication state:
   *
   * <ul>
   *   <li><b>"pending"</b> - Waiting for device approval
   *   <li><b>"accepted"</b> - Device approved (not used, returns token directly)
   *   <li><b>"rejected"</b> - Device rejected (not used, returns error)
   * </ul>
   */
  @Schema(
      description = "Authentication attempt status",
      example = "pending",
      allowableValues = {"pending", "accepted", "rejected"},
      required = false)
  private String status;

  /** Default constructor for JSON serialization. */
  public AdminLoginResponseDto() {
    // Default constructor
  }

  /**
   * Constructs a successful login response.
   *
   * @param token the bearer token
   * @param adminType the administrator type
   * @param username the administrator username
   * @param expiresAt the token expiration time
   */
  public AdminLoginResponseDto(
      String token, String adminType, String username, LocalDateTime expiresAt) {
    this.success = true;
    this.token = token;
    this.adminType = adminType;
    this.username = username;
    this.expiresAt = expiresAt;
    this.message = "Authentication successful";
  }

  /**
   * Constructs an error response.
   *
   * @param message the error message
   */
  public AdminLoginResponseDto(String message) {
    this.success = false;
    this.message = message;
  }

  // Getters and Setters

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getAdminType() {
    return adminType;
  }

  public void setAdminType(String adminType) {
    this.adminType = adminType;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Integer getAuthAttemptId() {
    return authAttemptId;
  }

  public void setAuthAttemptId(Integer authAttemptId) {
    this.authAttemptId = authAttemptId;
  }

  public Integer getChallengeCode() {
    return challengeCode;
  }

  public void setChallengeCode(Integer challengeCode) {
    this.challengeCode = challengeCode;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return "AdminLoginResponseDto{"
        + "success="
        + success
        + ", token='"
        + (token != null ? "[PROTECTED]" : "null")
        + '\''
        + ", adminType='"
        + adminType
        + '\''
        + ", username='"
        + username
        + '\''
        + ", expiresAt="
        + expiresAt
        + ", message='"
        + message
        + '\''
        + ", authAttemptId="
        + authAttemptId
        + ", challengeCode="
        + (challengeCode != null ? "[PROTECTED]" : "null")
        + ", status='"
        + status
        + '\''
        + '}';
  }
}
