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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Unit tests for {@link ClaimExpiresAtResolver} (#895 PR-C Patrick review).
 *
 * @since 2026
 */
class ClaimExpiresAtResolverTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("resolve prefers server expiresAt over local TTL")
  void resolvePrefersServerExpiresAt() {
    Instant claimedAt = Instant.parse("2026-10-19T12:00:00Z");
    Instant serverExpires = Instant.parse("2026-10-19T12:01:30Z");

    Instant resolved =
        ClaimExpiresAtResolver.resolve(serverExpires, claimedAt, Duration.ofSeconds(120));

    assertThat(resolved).isEqualTo(serverExpires);
  }

  @Test
  @DisplayName("resolve falls back to claimedAt + local TTL when server omitted")
  void resolveFallsBackToLocalTtl() {
    Instant claimedAt = Instant.parse("2026-10-19T12:00:00Z");

    Instant resolved = ClaimExpiresAtResolver.resolve(null, claimedAt, Duration.ofSeconds(90));

    assertThat(resolved).isEqualTo(claimedAt.plusSeconds(90));
  }

  @Test
  @DisplayName("parseOptionalExpiresAt reads Instant ISO-8601 from pending JSON")
  void parseOptionalExpiresAtFromInstantString() {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("authAttemptId", 1);
    body.put("expiresAt", "2026-10-19T12:02:00Z");

    assertThat(ClaimExpiresAtResolver.parseOptionalExpiresAt(body))
        .isEqualTo(Instant.parse("2026-10-19T12:02:00Z"));
  }

  @Test
  @DisplayName("parseOptionalExpiresAt returns null when field absent")
  void parseOptionalExpiresAtAbsent() {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("authAttemptId", 1);

    assertThat(ClaimExpiresAtResolver.parseOptionalExpiresAt(body)).isNull();
  }

  @Test
  @DisplayName("toPendingAuthPull preserves expiresAt alongside typed DTO")
  void toPendingAuthPullCapturesExpiresAt() {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("authAttemptId", 77);
    body.put("authAttemptProofToken", "tok");
    body.put("authAttemptProofTokenSignedByIntegration", "sig");
    body.put("authAttemptChallengeRequired", false);
    body.put("expiresAt", "2026-10-19T12:01:30.000Z");

    PendingAuthPull pull = AuthApiService.toPendingAuthPull(body);

    assertThat(pull.response().getAuthAttemptId()).isEqualTo(77);
    assertThat(pull.expiresAt()).isEqualTo(Instant.parse("2026-10-19T12:01:30.000Z"));
  }
}
