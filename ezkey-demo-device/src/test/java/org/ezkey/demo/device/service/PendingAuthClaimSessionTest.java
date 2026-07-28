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
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(1, 42, "proof-token", false, "Title", "Message", Instant.now());

    PendingAuthClaimSession.store(session, claim);

    assertThat(
            PendingAuthClaimSession.findOpen(
                session, 1, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL))
        .contains(claim);
  }

  @Test
  void findOpen_returnsEmpty_whenExpired_andClearsAttribute() {
    MockHttpSession session = new MockHttpSession();
    Instant claimedAt = Instant.parse("2026-07-28T12:00:00Z");
    ClaimedPendingAuth claim =
        new ClaimedPendingAuth(1, 42, "proof-token", true, null, null, claimedAt);
    PendingAuthClaimSession.store(session, claim);

    Instant now = claimedAt.plus(PendingAuthClaimSession.DEFAULT_TTL).plusSeconds(1);

    assertThat(
            PendingAuthClaimSession.findOpen(session, 1, now, PendingAuthClaimSession.DEFAULT_TTL))
        .isEmpty();
    assertThat(session.getAttribute(PendingAuthClaimSession.attrKey(1))).isNull();
  }

  @Test
  void clear_removesClaim() {
    MockHttpSession session = new MockHttpSession();
    PendingAuthClaimSession.store(session, ClaimedPendingAuth.of(7, 9, "tok", false, null, null));

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
