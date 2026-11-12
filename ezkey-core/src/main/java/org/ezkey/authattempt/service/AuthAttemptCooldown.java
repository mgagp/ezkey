/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: AuthAttemptCooldown
 * Description: Derives a creation suspension from existing attempt timestamps.
 */

package org.ezkey.authattempt.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

/**
 * Pure cooldown calculation over existing attempt timestamps.
 *
 * <p>No counter is stored. A suspension starts when {@code threshold} events fall inside {@code
 * windowSeconds} ending at one of those events, and it lasts {@code durationSeconds} from that
 * event. The suspension expires on its own. Timestamps are attempt {@code createdAt} values: status
 * changes at most one attempt TTL after creation, so the window is approximate by at most that TTL.
 *
 * @since 2026
 */
public final class AuthAttemptCooldown {

  private AuthAttemptCooldown() {}

  /**
   * Remaining suspension, if the events still hold one open.
   *
   * @param eventTimes createdAt of counted attempts; null entries are ignored
   * @param now evaluation instant
   * @param threshold number of events inside the window that open a suspension
   * @param windowSeconds length of the counting window
   * @param durationSeconds how long a crossing suspends further creates
   * @return seconds until the latest suspension ends, at least 1, or empty when creates may proceed
   */
  public static OptionalLong remainingSeconds(
      List<OffsetDateTime> eventTimes,
      OffsetDateTime now,
      int threshold,
      int windowSeconds,
      int durationSeconds) {
    if (eventTimes == null || threshold <= 0 || windowSeconds <= 0 || durationSeconds <= 0) {
      return OptionalLong.empty();
    }
    List<OffsetDateTime> events = new ArrayList<>();
    for (OffsetDateTime eventTime : eventTimes) {
      if (eventTime != null) {
        events.add(eventTime);
      }
    }
    if (events.size() < threshold) {
      return OptionalLong.empty();
    }
    events.sort(Comparator.naturalOrder());
    OffsetDateTime cooldownUntil = null;
    for (int i = 0; i < events.size(); i++) {
      OffsetDateTime event = events.get(i);
      OffsetDateTime windowStart = event.minusSeconds(windowSeconds);
      int count = 0;
      for (int j = i; j >= 0; j--) {
        OffsetDateTime candidate = events.get(j);
        if (candidate.isBefore(windowStart)) {
          break;
        }
        count++;
      }
      if (count >= threshold) {
        OffsetDateTime end = event.plusSeconds(durationSeconds);
        if (cooldownUntil == null || end.isAfter(cooldownUntil)) {
          cooldownUntil = end;
        }
      }
    }
    if (cooldownUntil == null || !now.isBefore(cooldownUntil)) {
      return OptionalLong.empty();
    }
    long seconds = Duration.between(now, cooldownUntil).getSeconds();
    return OptionalLong.of(Math.max(1L, seconds));
  }
}
