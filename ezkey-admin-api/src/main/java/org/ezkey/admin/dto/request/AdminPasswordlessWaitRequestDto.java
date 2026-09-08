/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminPasswordlessWaitRequestDto
 * Description: Request DTO for waiting for passwordless authentication completion.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for waiting for passwordless authentication completion.
 *
 * <p>Supports two authentication flows:
 *
 * <ul>
 *   <li><b>Challenge Flow:</b> Used when challengeRequested=true in login. Client receives
 *       authAttemptId, challengeCode, and waiterSecret from /login endpoint, displays the challenge
 *       to the user, then calls this endpoint with all three values to wait for device approval.
 *   <li><b>Non-Blocking Flow:</b> Used when nonBlocking=true in login. Client receives
 *       authAttemptId and waiterSecret from /login endpoint and calls this endpoint with
 *       authAttemptId and waiterSecret (no challengeCode) to poll for device response.
 * </ul>
 *
 * <p><b>Security (Waiter Secret &amp; Challenge Flow):</b> The waiterSecret acts as a one-time
 * capability token proving that the caller is the exact client that initiated this login attempt.
 * When challenge flow is requested, the challengeCode provides additional verification.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @param authAttemptId Authentication attempt ID from the login response (required)
 * @param challengeCode Challenge code from the login response (optional - required only for
 *     challenge flow, null for non-blocking flow)
 * @param waiterSecret One-time waiter secret capability received from /login (required)
 */
public record AdminPasswordlessWaitRequestDto(
    @NotNull(message = "Auth attempt ID is required") Integer authAttemptId,
    Integer challengeCode,
    @NotBlank(message = "Waiter secret is required") String waiterSecret) {

  /**
   * Returns a string representation of the passwordless wait request with protected credentials.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "AdminPasswordlessWaitRequestDto{"
        + "authAttemptId="
        + authAttemptId
        + ", challengeCode=[PROTECTED]"
        + ", waiterSecret=[PROTECTED]"
        + '}';
  }
}
