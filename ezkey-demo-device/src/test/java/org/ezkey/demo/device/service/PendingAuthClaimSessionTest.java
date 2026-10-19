/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PendingAuthClaimSessionTest {

  @Test
  void store_then_findOpen_returnsClaim() {
    MockHttpSession session = new MockHttpSession();
    Instant claimedAt = Instant.now();
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(
            1, 42, "proof-token", false, "Title", "Message", claimedAt, claimedAt.plusSeconds(120));

    PendingAuthClaimSession.store(session, claim);

    assertThat(
            PendingAuthClaimSession.findOpen(
                session, 1, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL))
        .contains(claim);
  }

  @Test
  void findOpen_returnsEmpty_whenSessionTtlExpired_andClearsAttribute() {
    MockHttpSession session = new MockHttpSession();
    Instant claimedAt = Instant.parse("2026-07-28T12:00:00Z");
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(
            1, 42, "proof-token", true, null, null, claimedAt, claimedAt.plusSeconds(120));
    PendingAuthClaimSession.store(session, claim);

    Instant now = claimedAt.plus(PendingAuthClaimSession.DEFAULT_TTL).plusSeconds(1);

    assertThat(
            PendingAuthClaimSession.findOpen(session, 1, now, PendingAuthClaimSession.DEFAULT_TTL))
        .isEmpty();
    assertThat(session.getAttribute(PendingAuthClaimSession.attrKey(1))).isNull();
  }

  @Test
  void findOpen_returnsEmpty_whenExpiresAtPassed_evenIfWithinSessionTtl() {
    MockHttpSession session = new MockHttpSession();
    Instant claimedAt = Instant.parse("2026-10-19T12:00:00Z");
    Instant expiresAt = claimedAt.plusSeconds(120);
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(1, 42, "proof-token", false, null, null, claimedAt, expiresAt);
    PendingAuthClaimSession.store(session, claim);

    // Still inside the 10 min session TTL, but past attempt expiresAt
    Instant now = expiresAt.plusSeconds(1);

    assertThat(
            PendingAuthClaimSession.findOpen(session, 1, now, PendingAuthClaimSession.DEFAULT_TTL))
        .isEmpty();
    assertThat(session.getAttribute(PendingAuthClaimSession.attrKey(1))).isNull();
  }

  @Test
  void findOpen_keepsClaim_whenExpiresAtInFuture_andWithinSessionTtl() {
    MockHttpSession session = new MockHttpSession();
    Instant claimedAt = Instant.parse("2026-10-19T12:00:00Z");
    Instant expiresAt = claimedAt.plusSeconds(120);
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(1, 42, "proof-token", false, null, null, claimedAt, expiresAt);
    PendingAuthClaimSession.store(session, claim);

    Instant now = claimedAt.plusSeconds(60);

    assertThat(
            PendingAuthClaimSession.findOpen(session, 1, now, PendingAuthClaimSession.DEFAULT_TTL))
        .contains(claim);
  }

  @Test
  void effectiveDeadline_isMinOfExpiresAtAndSessionTtl() {
    Instant claimedAt = Instant.parse("2026-10-19T12:00:00Z");
    Instant expiresAt = claimedAt.plusSeconds(90);
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(1, 42, "tok", false, null, null, claimedAt, expiresAt);

    assertThat(PendingAuthClaimSession.effectiveDeadline(claim, Duration.ofMinutes(10)))
        .isEqualTo(expiresAt);

    Instant farExpires = claimedAt.plus(Duration.ofMinutes(30));
    ClaimedPendingAuth longLived =
        new ClaimedPendingAuth(1, 42, "tok", false, null, null, claimedAt, farExpires);
    assertThat(PendingAuthClaimSession.effectiveDeadline(longLived, Duration.ofMinutes(10)))
        .isEqualTo(claimedAt.plus(Duration.ofMinutes(10)));
  }

  @Test
  void effectiveDeadline_usesSessionTtl_whenExpiresAtNull() {
    Instant claimedAt = Instant.parse("2026-10-19T12:00:00Z");
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(1, 42, "tok", false, null, null, claimedAt, null);

    assertThat(PendingAuthClaimSession.effectiveDeadline(claim, Duration.ofMinutes(10)))
        .isEqualTo(claimedAt.plus(Duration.ofMinutes(10)));
  }

  @Test
  void clear_removesClaim() {
    MockHttpSession session = new MockHttpSession();
    Instant expiresAt = Instant.now().plusSeconds(60);
    PendingAuthClaimSession.store(
        session, ClaimedPendingAuth.of(7, 9, "tok", false, null, null, expiresAt));

    PendingAuthClaimSession.clear(session, 7);

    assertThat(PendingAuthClaimSession.findOpen(session, 7, Instant.now(), Duration.ofMinutes(10)))
        .isEmpty();
  }

  @Test
  void findOpen_ignoresNullSession() {
    assertThat(
            PendingAuthClaimSession.findOpen(
                null, 1, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL))
        .isEmpty();
  }
}
