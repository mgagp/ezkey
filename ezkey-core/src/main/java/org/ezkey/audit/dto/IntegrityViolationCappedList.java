/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrityViolationCappedList
 * Description: Capped violation list with totals for alert and audit payloads.
 */

package org.ezkey.audit.dto;

import java.util.List;

/**
 * Violation list with explicit cap metadata (B2.5 investigation operability).
 *
 * @param items returned slice (may be capped)
 * @param totalCount full number of violations detected
 * @param returnedCount {@code items.size()}
 * @param truncated true when {@code totalCount > returnedCount}
 * @param <T> violation item type
 * @since 2026
 */
public record IntegrityViolationCappedList<T>(
    List<T> items, int totalCount, int returnedCount, boolean truncated) {

  /** Alert payload caps (TB B2.5 D2). */
  public static final int ALERT_ENTRY_CAP = 25;

  public static final int ALERT_CHAIN_CAP = 10;

  /** Nightly completion audit event caps (TB B2.5 D2). */
  public static final int EVENT_ENTRY_CAP = 10;

  public static final int EVENT_CHAIN_CAP = 5;

  /**
   * Builds a capped view of {@code all} for serialization.
   *
   * @param all full violation list
   * @param maxItems maximum items to include (use {@link Integer#MAX_VALUE} for no cap)
   * @param <T> item type
   * @return capped list metadata
   */
  public static <T> IntegrityViolationCappedList<T> of(List<T> all, int maxItems) {
    int total = all.size();
    if (maxItems <= 0 || total <= maxItems) {
      return new IntegrityViolationCappedList<>(List.copyOf(all), total, total, false);
    }
    return new IntegrityViolationCappedList<>(
        List.copyOf(all.subList(0, maxItems)), total, maxItems, true);
  }

  /**
   * Uncapped list for API verification responses.
   *
   * @param all all violations
   * @param <T> item type
   * @return list with {@code truncated=false}
   */
  public static <T> IntegrityViolationCappedList<T> uncapped(List<T> all) {
    return of(all, Integer.MAX_VALUE);
  }
}
