/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.service;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * HTTP session helpers for {@link ClaimedPendingAuth}.
 *
 * <p>Keeps a short-lived open claim per enrollment so Demo Device UI survives a second GET after
 * Auth API has already moved the attempt to {@code READ}.
 *
 * @since 2026
 */
public final class PendingAuthClaimSession {

  /** Max time a claimed pending may be rehydrated without a new Auth API pending call. */
  public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

  private static final String ATTR_PREFIX = "ezkey.demo.claimedPending.";

  private PendingAuthClaimSession() {}

  /**
   * Returns an open (non-expired) claim for the enrollment, if present.
   *
   * <p>Expired entries are removed from the session.
   *
   * @param session HTTP session (may be {@code null})
   * @param enrollmentId enrollment id
   * @param now clock instant
   * @param ttl maximum age of a stored claim
   * @return open claim, or empty
   */
  public static Optional<ClaimedPendingAuth> findOpen(
      HttpSession session, Integer enrollmentId, Instant now, Duration ttl) {
    if (session == null || enrollmentId == null) {
      return Optional.empty();
    }
    Object raw = session.getAttribute(attrKey(enrollmentId));
    if (!(raw instanceof ClaimedPendingAuth claim)) {
      return Optional.empty();
    }
    if (!enrollmentId.equals(claim.enrollmentId())) {
      clear(session, enrollmentId);
      return Optional.empty();
    }
    if (claim.claimedAt() == null || claim.claimedAt().plus(ttl).isBefore(now)) {
      clear(session, enrollmentId);
      return Optional.empty();
    }
    return Optional.of(claim);
  }

  /**
   * Stores a successful pending claim for later UI rehydration.
   *
   * @param session HTTP session
   * @param claim claim to store
   */
  public static void store(HttpSession session, ClaimedPendingAuth claim) {
    if (session == null || claim == null || claim.enrollmentId() == null) {
      return;
    }
    session.setAttribute(attrKey(claim.enrollmentId()), claim);
  }

  /**
   * Removes any stored claim for the enrollment (after respond or expiry).
   *
   * @param session HTTP session (may be {@code null})
   * @param enrollmentId enrollment id
   */
  public static void clear(HttpSession session, Integer enrollmentId) {
    if (session == null || enrollmentId == null) {
      return;
    }
    session.removeAttribute(attrKey(enrollmentId));
  }

  static String attrKey(Integer enrollmentId) {
    return ATTR_PREFIX + enrollmentId;
  }
}
