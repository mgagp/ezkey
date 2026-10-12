/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: IntegrityWindowOverCapException
 * Description: Thrown when an Integrity window exceeds the configured days-based cap.
 */

package org.ezkey.audit.exception;

import java.io.Serial;

/**
 * Thrown when a requested Integrity {@code [from, to)} window exceeds the configured maximum days
 * (report GETs, async VERIFY / RUN_VALIDATION starts, operator retroactive POST).
 *
 * <p>Map to HTTP 400 with RFC 9457 type {@link #TYPE_URI} and extension {@code maxWindowDays} in
 * the Admin API.
 *
 * @since 2026
 */
public class IntegrityWindowOverCapException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** RFC 9457 problem type for over-cap refusals. */
  public static final String TYPE_URI =
      "https://ezkey.io/problems/domain/integrity-window-over-cap";

  private final int maxWindowDays;

  /**
   * Creates the exception with a stable operator-facing detail.
   *
   * @param maxWindowDays configured maximum window length in calendar days
   */
  public IntegrityWindowOverCapException(int maxWindowDays) {
    super(
        "Verification window exceeds maximum of "
            + maxWindowDays
            + " calendar days (DST-tolerant Instant span). Narrow the range.");
    this.maxWindowDays = maxWindowDays;
  }

  /**
   * Returns the configured maximum window length in calendar days.
   *
   * @return max window days
   */
  public int getMaxWindowDays() {
    return maxWindowDays;
  }
}
