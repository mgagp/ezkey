/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminChallengeMismatchException
 * Description: Thrown when the code entered on the device did not match the admin login challenge.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when an admin passwordless login attempt ended {@code INVALID}: the device
 * answered with a valid signature, but the challenge code entered on it did not match.
 *
 * <p>{@code INVALID} is set only after ownership is proven (an invalid device signature keeps the
 * attempt {@code READ} and gets the generic Auth API 409, #912). The reader of this error is the
 * initiator holding the waiter secret, who displays the code; naming the mismatch reveals nothing
 * beyond the outcome type already returned (written security review on #1465). Never put the
 * expected or entered digits in the message.
 *
 * <p><b>HTTP Status:</b> 400 Bad Request
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/challenge-mismatch`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/challenge-mismatch",
 * "title": "Challenge Mismatch",
 * "status": 400,
 * "detail": "The code entered on the device did not match",
 * "instance": "/api/v1/admin/auth/passwordless-wait"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.http.ProblemDetail
 */
public class AdminChallengeMismatchException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Client-facing detail; contains no digits. */
  public static final String DETAIL = "The code entered on the device did not match";

  /** Constructs the exception with the fixed client-facing {@link #DETAIL}. */
  public AdminChallengeMismatchException() {
    super(DETAIL);
  }
}
