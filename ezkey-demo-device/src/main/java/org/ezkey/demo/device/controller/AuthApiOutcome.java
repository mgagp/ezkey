/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.controller;

/**
 * Package-private Auth API HTTP outcome for Demo Device UI mapping (#943).
 *
 * <p>Aligned with mobile {@code authApiProblem.ts} catalog keys where applicable. Tolerant of
 * absent or older ProblemDetail {@code type} values.
 *
 * @since 2026
 */
enum AuthApiOutcome {
  /** Wall-clock expiry — {@code auth-attempt-expired} (#895). */
  EXPIRED,
  /** State conflict / cancel / decided / #916 signature refuse — unified. */
  NO_LONGER_ACTIVE,
  /** Missing integration signing key — HTTP 503 (#844). */
  SIGNING_UNAVAILABLE,
  /** Rate limit — HTTP 429. */
  RATE_LIMITED,
  /** HTTP 200 sent but confirmation unverifiable, or ambiguous transport/5xx after send (Q9). */
  UNCONFIRMED,
  /** Other HTTP error with a status code. */
  HTTP_OTHER,
  /** Transport / timeout with no HTTP response. */
  TRANSPORT
}
