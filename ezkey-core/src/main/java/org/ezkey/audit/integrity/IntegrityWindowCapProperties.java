/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: IntegrityWindowCapProperties
 * Description: Single days-based window cap for Integrity heavy-crypto paths.
 */

package org.ezkey.audit.integrity;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.ezkey.audit.exception.IntegrityWindowOverCapException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Single configurable window cap for Integrity paths that recompute under {@link
 * IntegrityHeavyCryptoGate}.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity} — same prefix as {@link
 * AuditHmacProperties}; this class binds only {@code max-window-days}. Spring Boot binds each
 * {@code @ConfigurationProperties} type independently, so the shared prefix does not collide.
 *
 * <p><b>Effective duration:</b> {@code max-window-days × 24 h + 1 h} — calendar days with DST
 * fall-back tolerance (preserves the #743 Instant-span rule at the raised default of 92 days).
 *
 * <p>Applies to: report GETs ({@code integrity-check}, {@code chain-integrity}), async VERIFY
 * starts, async {@code RUN_VALIDATION}, and operator {@code POST …/integrity-validation/run}.
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity")
@Validated
public class IntegrityWindowCapProperties {

  /** Default maximum window length in calendar days (DST-tolerant Instant span). */
  public static final int DEFAULT_MAX_WINDOW_DAYS = 92;

  /**
   * Maximum {@code [from, to)} length in calendar days. Effective Instant cap is {@link
   * #maxWindow()}. Primitive {@code int} so an empty env value cannot remove the bound.
   */
  @Min(1)
  private int maxWindowDays = DEFAULT_MAX_WINDOW_DAYS;

  /**
   * Returns the configured maximum window length in calendar days.
   *
   * @return max window days
   */
  public int getMaxWindowDays() {
    return maxWindowDays;
  }

  /**
   * Sets the maximum window length in calendar days.
   *
   * @param maxWindowDays max days ({@code >= 1})
   */
  public void setMaxWindowDays(int maxWindowDays) {
    this.maxWindowDays = maxWindowDays;
  }

  /**
   * Effective Instant-length cap: {@code days × 24 h + 1 h} so a calendar-day span across a
   * fall-back DST transition stays within the configured days bound.
   *
   * @return maximum allowed {@code Duration.between(from, to)}
   */
  public Duration maxWindow() {
    return Duration.ofHours((long) maxWindowDays * 24L + 1L);
  }

  /**
   * Validates bounds: required range, ordered ends, and hard window cap.
   *
   * @param from inclusive window start
   * @param to exclusive window end
   * @throws IllegalArgumentException when bounds are missing or inverted
   * @throws IntegrityWindowOverCapException when the window exceeds {@link #maxWindow()}
   */
  public void validateWindow(OffsetDateTime from, OffsetDateTime to) {
    if (from == null || to == null) {
      throw new IllegalArgumentException(
          "Date range is required for verification. Provide from (inclusive) and to (exclusive) as"
              + " ISO-8601.");
    }
    if (!to.isAfter(from)) {
      throw new IllegalArgumentException(
          "Invalid date range: to must be after from (exclusive end, inclusive start).");
    }
    Duration duration = Duration.between(from, to);
    if (duration.compareTo(maxWindow()) > 0) {
      throw new IntegrityWindowOverCapException(maxWindowDays);
    }
  }
}
