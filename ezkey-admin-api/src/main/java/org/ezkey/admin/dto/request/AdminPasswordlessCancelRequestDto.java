/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminPasswordlessCancelRequestDto
 * Description: Request DTO for cancelling a pending passwordless login attempt.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for cancelling a pending passwordless authentication attempt.
 *
 * <p>Used when the operator aborts the login wait (Admin UI Cancel). Requires the same waiter
 * secret issued at {@code /login} so only the initiating client can cancel (#831).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 * @param authAttemptId Authentication attempt ID from the login response (required)
 * @param waiterSecret One-time waiter secret capability received from /login (required)
 */
public record AdminPasswordlessCancelRequestDto(
    @NotNull(message = "Auth attempt ID is required") Integer authAttemptId,
    @NotBlank(message = "Waiter secret is required") String waiterSecret) {

  /**
   * Returns a string representation with the waiter secret redacted.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "AdminPasswordlessCancelRequestDto{"
        + "authAttemptId="
        + authAttemptId
        + ", waiterSecret=[PROTECTED]"
        + '}';
  }
}
