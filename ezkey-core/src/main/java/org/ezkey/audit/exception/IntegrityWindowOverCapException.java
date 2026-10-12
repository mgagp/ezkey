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
 * Thrown when a requested Integrity {@code [from, to)} window exceeds the applicable configured
 * maximum days — synchronous paths (report GETs and operator {@code POST
 * …/integrity-validation/run}) use {@code max-sync-window-days}; async VERIFY / {@code
 * RUN_VALIDATION} starts use {@code max-window-days}.
 *
 * <p>Map to HTTP 400 with RFC 9457 type {@link #TYPE_URI} and extensions {@code maxWindowDays} and
 * {@code capKind} ({@link #CAP_KIND_SYNC} or {@link #CAP_KIND_ASYNC}) in the Admin API. Sync
 * over-cap responses also include extension {@code asyncAlternative} with the fixed async job start
 * path ({@link #ASYNC_ALTERNATIVE}) — never derived from request input.
 *
 * @since 2026
 */
public class IntegrityWindowOverCapException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** RFC 9457 problem type for over-cap refusals. */
  public static final String TYPE_URI =
      "https://ezkey.io/problems/domain/integrity-window-over-cap";

  /**
   * Fixed operator pointer to the async Integrity job start (sync over-cap only). Not built from
   * request input.
   */
  public static final String ASYNC_ALTERNATIVE = "POST /api/v1/audit-logs/integrity/jobs";

  /**
   * ProblemDetail {@code capKind} when the sync cap was exceeded (synchronous paths: report GETs and
   * operator {@code POST …/integrity-validation/run}).
   */
  public static final String CAP_KIND_SYNC = "SYNC";

  /** ProblemDetail {@code capKind} when the async / operator cap was exceeded. */
  public static final String CAP_KIND_ASYNC = "ASYNC";

  private final int maxWindowDays;

  private final String capKind;

  private final String asyncAlternative;

  /**
   * Creates an async-path over-cap exception (no {@code asyncAlternative} extension).
   *
   * @param maxWindowDays configured maximum window length in calendar days
   */
  public IntegrityWindowOverCapException(int maxWindowDays) {
    this(maxWindowDays, CAP_KIND_ASYNC, null);
  }

  /**
   * Creates a synchronous-path over-cap exception with a fixed async alternative pointer (report
   * GETs and operator {@code POST …/integrity-validation/run}).
   *
   * @param maxSyncWindowDays configured sync maximum window length in calendar days
   * @return exception carrying {@link #CAP_KIND_SYNC} and {@link #ASYNC_ALTERNATIVE}
   */
  public static IntegrityWindowOverCapException forSyncCap(int maxSyncWindowDays) {
    return new IntegrityWindowOverCapException(maxSyncWindowDays, CAP_KIND_SYNC, ASYNC_ALTERNATIVE);
  }

  private IntegrityWindowOverCapException(
      int maxWindowDays, String capKind, String asyncAlternative) {
    super(
        "Verification window exceeds maximum of "
            + maxWindowDays
            + " calendar days (DST-tolerant Instant span). Narrow the range.");
    this.maxWindowDays = maxWindowDays;
    this.capKind = capKind;
    this.asyncAlternative = asyncAlternative;
  }

  /**
   * Returns the configured maximum window length in calendar days.
   *
   * @return max window days
   */
  public int getMaxWindowDays() {
    return maxWindowDays;
  }

  /**
   * Returns which cap was exceeded ({@link #CAP_KIND_SYNC} or {@link #CAP_KIND_ASYNC}).
   *
   * @return cap kind constant
   */
  public String getCapKind() {
    return capKind;
  }

  /**
   * Returns the fixed async alternative path for sync over-cap, or {@code null} on the async path.
   *
   * @return {@link #ASYNC_ALTERNATIVE} or {@code null}
   */
  public String getAsyncAlternative() {
    return asyncAlternative;
  }
}
