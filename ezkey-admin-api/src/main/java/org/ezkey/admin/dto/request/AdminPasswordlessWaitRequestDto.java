/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminPasswordlessWaitRequestDto
 * Description: Request DTO for waiting for passwordless authentication completion.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for waiting for passwordless authentication completion.
 *
 * <p>Supports two authentication flows:
 *
 * <ul>
 *   <li><b>Challenge Flow:</b> Used when challengeRequested=true in login. Client receives both
 *       authAttemptId and challengeCode from /login endpoint, displays the challenge to the user,
 *       then calls this endpoint with both values to wait for device approval.
 *   <li><b>Non-Blocking Flow:</b> Used when nonBlocking=true in login. Client receives only
 *       authAttemptId from /login endpoint and calls this endpoint with authAttemptId only (no
 *       challengeCode) to poll for device response.
 * </ul>
 *
 * <p><b>Security (Challenge Flow):</b> The challengeCode acts as proof that the client legitimately
 * initiated the authentication request, preventing enumeration attacks on authAttemptId. Only the
 * client that received the challenge from /login can proceed.
 *
 * <p><b>Anti-Enumeration Protection:</b> In challenge flow, without the challengeCode requirement,
 * an attacker could enumerate authAttemptId values (1, 2, 3...) and attempt to hijack ongoing
 * authentication attempts. The challengeCode provides cryptographic proof of legitimacy with
 * 1/1,000,000 probability of guessing (6 digits).
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @param authAttemptId Authentication attempt ID from the login response (required)
 * @param challengeCode Challenge code from the login response (optional - required only for
 *     challenge flow, null for non-blocking flow)
 */
public record AdminPasswordlessWaitRequestDto(
    @NotNull(message = "Auth attempt ID is required") Integer authAttemptId,
    Integer challengeCode) {

  /**
   * Returns a string representation of the passwordless wait request.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "AdminPasswordlessWaitRequestDto{"
        + "authAttemptId="
        + authAttemptId
        + ", challengeCode=[PROTECTED]"
        + '}';
  }
}
