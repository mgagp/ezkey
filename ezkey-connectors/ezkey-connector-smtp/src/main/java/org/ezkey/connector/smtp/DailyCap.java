/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Sends per UTC day that may reach the provider. It bounds what a stolen connector token can send,
 * since such calls bypass the core's caps. The count lives in memory and restarts at zero with the
 * connector: a simple bound, not the main protection.
 */
final class DailyCap {

  private final int limit;
  private final Clock clock;
  private LocalDate day;
  private int used;

  DailyCap(int limit, Clock clock) {
    this.limit = limit;
    this.clock = clock;
  }

  /** Takes one send from today's allowance, or returns false when it is used up. */
  synchronized boolean tryAcquire() {
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    if (!today.equals(day)) {
      day = today;
      used = 0;
    }
    if (used >= limit) {
      return false;
    }
    used++;
    return true;
  }

  /** Gives a send back when it did not leave, so a rejected attempt does not count. */
  synchronized void release() {
    if (used > 0) {
      used--;
    }
  }
}
