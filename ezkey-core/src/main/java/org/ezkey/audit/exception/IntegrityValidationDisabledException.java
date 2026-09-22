/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: IntegrityValidationDisabledException
 * Description: Thrown when retroactive integrity validation is requested while nightly is off.
 */

package org.ezkey.audit.exception;

import java.io.Serial;

/**
 * Thrown when retroactive integrity validation is requested while {@code
 * ezkey.audit.integrity.nightly.enabled} is off.
 *
 * <p>Typical on the opt-in <strong>base</strong> runtime profile. Gate on that flag, not on the
 * product profile name. Covers both the nightly scheduler path and operator {@code POST
 * …/integrity-validation/run}. Map to HTTP 409 with RFC 9457 {@code ProblemDetail} in the Admin
 * API.
 *
 * @since 2026
 */
public class IntegrityValidationDisabledException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception with the stable operator-facing message. */
  public IntegrityValidationDisabledException() {
    super("Nightly integrity validation is inactive on this instance.");
  }
}
