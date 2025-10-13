/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptWaitResponseDto
 * Description: Response DTO for waiting for authentication response in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Response DTO for waiting for authentication response in admin API.
 *
 * <p>This DTO represents the response from the authentication wait endpoint that provides the final
 * authentication status after polling for completion. It includes both the raw authentication
 * attempt data and a calculated status field for easy consumption by integrating applications.
 *
 * <p><b>Usage Context:</b> Returned by the wait endpoint to provide applications with the final
 * authentication result after the device has responded or timeout has been reached. The calculated
 * status field simplifies integration logic.
 *
 * <p><b>Status Calculation Rules (from ENDPOINT.md):</b>
 *
 * <ol>
 *   <li><b>PENDING:</b> authAttemptRead is null or false
 *   <li><b>READ:</b> authAttemptRead is true and authAttemptResponded is null or false
 *   <li><b>INVALID:</b> authAttemptValid is null or false
 *   <li><b>REJECTED:</b> authAttemptAccepted is null or false
 *   <li><b>ACCEPTED:</b> authAttemptAccepted is true
 * </ol>
 *
 * <p><b>Response Fields:</b>
 *
 * <ul>
 *   <li><b>authAttempt:</b> Complete authentication attempt data
 *   <li><b>status:</b> Calculated status based on authentication state
 *   <li><b>completed:</b> Whether the authentication process is complete
 *   <li><b>timeoutReached:</b> Whether the wait ended due to timeout
 *   <li><b>waitDuration:</b> Actual duration waited in seconds
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptDto
 * @see AuthAttemptWaitRequestDto
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
@Schema(description = "Response DTO for authentication wait operation")
public class AuthAttemptWaitResponseDto {

  /**
   * Complete authentication attempt data. Contains all the raw authentication attempt information
   * for detailed processing.
   */
  @Schema(description = "Complete authentication attempt data")
  private AuthAttemptDto authAttempt;

  /**
   * Calculated authentication status based on the rules defined in ENDPOINT.md. Provides a
   * simplified status for easy integration logic.
   *
   * <p><b>Possible Values:</b>
   *
   * <ul>
   *   <li><b>PENDING:</b> Authentication request created but not yet read by device
   *   <li><b>READ:</b> Device has read the request but not yet responded
   *   <li><b>INVALID:</b> Authentication was invalid (wrong signature, challenge, etc.)
   *   <li><b>REJECTED:</b> User rejected the authentication request
   *   <li><b>ACCEPTED:</b> User accepted the authentication request
   * </ul>
   */
  @Schema(
      description = "Calculated authentication status",
      example = "ACCEPTED",
      allowableValues = {"PENDING", "READ", "INVALID", "REJECTED", "ACCEPTED"})
  private String status;

  /**
   * Indicates whether the authentication process is complete. True when the device has responded
   * (status is ACCEPTED, REJECTED, or INVALID).
   */
  @Schema(description = "Whether authentication process is complete", example = "true")
  private Boolean completed;

  /**
   * Indicates whether the wait operation ended due to timeout. True when the maximum wait duration
   * was reached before completion.
   */
  @Schema(description = "Whether wait ended due to timeout", example = "false")
  private Boolean timeoutReached;

  /**
   * Actual duration waited in seconds before returning the response. Useful for monitoring and
   * debugging wait operations.
   */
  @Schema(description = "Actual duration waited in seconds", example = "15")
  private Integer waitDuration;

  /** Timestamp when the wait operation completed. Used for auditing and monitoring purposes. */
  @Schema(description = "Timestamp when wait operation completed")
  private LocalDateTime completedAt;

  /** Default constructor for AuthAttemptWaitResponseDto. */
  public AuthAttemptWaitResponseDto() {}

  /**
   * Constructor with all required fields.
   *
   * @param authAttempt the authentication attempt data
   * @param status the calculated status
   * @param completed whether authentication is complete
   * @param timeoutReached whether timeout was reached
   * @param waitDuration actual wait duration in seconds
   * @param completedAt timestamp when wait completed
   */
  public AuthAttemptWaitResponseDto(
      AuthAttemptDto authAttempt,
      String status,
      Boolean completed,
      Boolean timeoutReached,
      Integer waitDuration,
      LocalDateTime completedAt) {
    this.authAttempt = authAttempt;
    this.status = status;
    this.completed = completed;
    this.timeoutReached = timeoutReached;
    this.waitDuration = waitDuration;
    this.completedAt = completedAt;
  }

  /**
   * Gets the authentication attempt data.
   *
   * @return the complete authentication attempt data
   */
  public AuthAttemptDto getAuthAttempt() {
    return authAttempt;
  }

  /**
   * Sets the authentication attempt data.
   *
   * @param authAttempt the authentication attempt data to set
   */
  public void setAuthAttempt(AuthAttemptDto authAttempt) {
    this.authAttempt = authAttempt;
  }

  /**
   * Gets the calculated authentication status.
   *
   * @return the calculated status (PENDING, READ, INVALID, REJECTED, ACCEPTED)
   */
  public String getStatus() {
    return status;
  }

  /**
   * Sets the calculated authentication status.
   *
   * @param status the calculated status to set
   */
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * Gets whether the authentication process is complete.
   *
   * @return true if authentication is complete, false otherwise
   */
  public Boolean getCompleted() {
    return completed;
  }

  /**
   * Sets whether the authentication process is complete.
   *
   * @param completed true if authentication is complete, false otherwise
   */
  public void setCompleted(Boolean completed) {
    this.completed = completed;
  }

  /**
   * Gets whether the wait operation ended due to timeout.
   *
   * @return true if timeout was reached, false otherwise
   */
  public Boolean getTimeoutReached() {
    return timeoutReached;
  }

  /**
   * Sets whether the wait operation ended due to timeout.
   *
   * @param timeoutReached true if timeout was reached, false otherwise
   */
  public void setTimeoutReached(Boolean timeoutReached) {
    this.timeoutReached = timeoutReached;
  }

  /**
   * Gets the actual wait duration in seconds.
   *
   * @return the wait duration in seconds
   */
  public Integer getWaitDuration() {
    return waitDuration;
  }

  /**
   * Sets the actual wait duration in seconds.
   *
   * @param waitDuration the wait duration in seconds to set
   */
  public void setWaitDuration(Integer waitDuration) {
    this.waitDuration = waitDuration;
  }

  /**
   * Gets the timestamp when the wait operation completed.
   *
   * @return the completion timestamp
   */
  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  /**
   * Sets the timestamp when the wait operation completed.
   *
   * @param completedAt the completion timestamp to set
   */
  public void setCompletedAt(LocalDateTime completedAt) {
    this.completedAt = completedAt;
  }
}
