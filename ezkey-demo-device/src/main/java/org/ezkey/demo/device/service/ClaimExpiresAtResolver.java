/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

/**
 * Resolves the session claim {@code expiresAt} for a Demo Device pending pull (#895 PR-C).
 *
 * <p>Prefer a server-provided {@code expiresAt} from the pending/claim JSON when present. Auth API
 * pending currently omits that field (#834); until it appears (or a fork exposes it), fall back to
 * {@code claimedAt + localTtl}. Document the drift risk: a local fallback longer than the real
 * attempt TTL (e.g. PAM at 90 s with a 120 s fallback) keeps a stale Approve/Deny visible until the
 * tighter bound elapses — respond 409 remains the fail-closed net.
 *
 * @since 2026
 */
public final class ClaimExpiresAtResolver {

  private ClaimExpiresAtResolver() {}

  /**
   * Chooses claim expiry: server value when non-null, otherwise {@code claimedAt + localTtl}.
   *
   * @param serverExpiresAt optional absolute expiry from the Auth API pending body
   * @param claimedAt instant the claim is stored in session
   * @param localTtl Demo Device fallback duration when the server omits expiry
   * @return non-null expiry instant for session invalidation
   */
  public static Instant resolve(Instant serverExpiresAt, Instant claimedAt, Duration localTtl) {
    if (serverExpiresAt != null) {
      return serverExpiresAt;
    }
    Instant base = claimedAt != null ? claimedAt : Instant.now();
    Duration ttl = localTtl != null ? localTtl : Duration.ofSeconds(120);
    return base.plus(ttl);
  }

  /**
   * Best-effort parse of {@code expiresAt} from a pending JSON object.
   *
   * <p>Accepts ISO-8601 instants ({@link Instant#parse}) and offset date-times. Returns {@code
   * null} when absent, blank, or unparseable — callers then use the local TTL fallback.
   *
   * @param pendingBody Auth API pending JSON root (may be {@code null})
   * @return parsed instant, or {@code null}
   */
  public static Instant parseOptionalExpiresAt(JsonNode pendingBody) {
    if (pendingBody == null || pendingBody.isNull() || !pendingBody.isObject()) {
      return null;
    }
    JsonNode node = pendingBody.get("expiresAt");
    if (node == null || node.isNull() || !node.isValueNode()) {
      return null;
    }
    String raw = node.asString();
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    try {
      return Instant.parse(trimmed);
    } catch (DateTimeParseException ignored) {
      // fall through to OffsetDateTime
    }
    try {
      return OffsetDateTime.parse(trimmed).toInstant();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }
}
