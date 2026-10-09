/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: IntegrityHeavyCryptoWindowLimits
 * Description: Shared default hour cap for synchronous Integrity heavy-crypto windows.
 */

package org.ezkey.audit.integrity;

/**
 * Shared default window cap for Integrity paths that recompute HMAC/chain work under {@link
 * IntegrityHeavyCryptoGate}.
 *
 * <p><b>Rule:</b> 8 calendar days, DST transition included — {@code 8 × 24 + 1 = 193} hours so the
 * Admin UI default 7-day lookback Instant span (inclusive calendar {@code from}/{@code to} with
 * exclusive-end conversion) never exceeds the cap across a fall-back transition.
 *
 * <p><b>Provisional:</b> {@code ezkey.audit.integrity.verify-report.max-window-hours} and {@code
 * ezkey.audit.integrity.retroactive.operator-max-window-hours} both default to this value today.
 * GitHub #730 will align them under a single {@code max-window-days} property so two hour keys do
 * not remain long-term.
 *
 * @since 2026
 */
public final class IntegrityHeavyCryptoWindowLimits {

  /**
   * Default maximum {@code [from, to)} length in hours for report GETs, async VERIFY starts, and
   * operator retroactive detect POST.
   */
  public static final int DEFAULT_MAX_WINDOW_HOURS = 193;

  private IntegrityHeavyCryptoWindowLimits() {}
}
