/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.service;

import java.time.Instant;

/**
 * Session-cached pending auth claim after a successful Auth API {@code pending} pull.
 *
 * <p>Auth API pending is read-once ({@code PENDING} → {@code READ}). Demo Device keeps the claim in
 * HTTP session so a second GET (double navigation, re-check, refresh) can re-show Approve/Deny
 * without calling pending again and getting HTTP 204.
 *
 * <p>{@code expiresAt} bounds how long that rehydration may hide a newer pending pull. Auth API
 * pending does not expose attempt expiry on the wire (#834); Demo Device therefore stores a session
 * bound aligned with the default auth-attempt TTL (see {@code
 * ezkey.demo-device.claimed-pending-attempt-ttl}). {@link PendingAuthClaimSession#findOpen}
 * invalidates at {@code min(expiresAt, claimedAt + 10 min)} when {@code expiresAt} is present.
 *
 * @param enrollmentId enrollment that claimed the attempt
 * @param authAttemptId claimed auth attempt id
 * @param authAttemptProofToken proof token required for respond
 * @param challengeRequired whether a challenge code is required
 * @param contextTitle optional business context title
 * @param contextMessage optional business context message
 * @param claimedAt instant when the claim was stored in session
 * @param expiresAt session invalidation instant for this claim; {@code null} means use only the
 *     session TTL cap
 * @since 2026
 */
public record ClaimedPendingAuth(
    Integer enrollmentId,
    Integer authAttemptId,
    String authAttemptProofToken,
    boolean challengeRequired,
    String contextTitle,
    String contextMessage,
    Instant claimedAt,
    Instant expiresAt) {

  /**
   * Creates a claim snapshot at the current instant.
   *
   * @param enrollmentId enrollment that claimed the attempt
   * @param authAttemptId claimed auth attempt id
   * @param authAttemptProofToken proof token required for respond
   * @param challengeRequired whether a challenge code is required
   * @param contextTitle optional business context title
   * @param contextMessage optional business context message
   * @param expiresAt session invalidation instant (may be {@code null})
   * @return claim with {@code claimedAt} set to {@link Instant#now()}
   */
  public static ClaimedPendingAuth of(
      Integer enrollmentId,
      Integer authAttemptId,
      String authAttemptProofToken,
      boolean challengeRequired,
      String contextTitle,
      String contextMessage,
      Instant expiresAt) {
    return new ClaimedPendingAuth(
        enrollmentId,
        authAttemptId,
        authAttemptProofToken,
        challengeRequired,
        contextTitle,
        contextMessage,
        Instant.now(),
        expiresAt);
  }
}
