/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: IntegrityWindowCapProperties
 * Description: Days-based async and sync window caps for Integrity heavy-crypto paths.
 */

package org.ezkey.audit.integrity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.ezkey.audit.exception.IntegrityWindowOverCapException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configurable window caps for Integrity paths that recompute under {@link
 * IntegrityHeavyCryptoGate}.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity} — same prefix as {@link
 * AuditHmacProperties}; this class binds {@code max-window-days} (async) and {@code
 * max-sync-window-days} (synchronous report GETs). Spring Boot binds each
 * {@code @ConfigurationProperties} type independently, so the shared prefix does not collide.
 *
 * <p><b>Two caps (Christophe M1 / maintainer option A):</b>
 *
 * <ul>
 *   <li>{@code max-window-days} (default 92) — async VERIFY starts, async {@code RUN_VALIDATION},
 *       and operator {@code POST …/integrity-validation/run}.
 *   <li>{@code max-sync-window-days} (default 8) — synchronous report GETs ({@code
 *       integrity-check}, {@code chain-integrity}). Preserves the #743 Instant-span floor so a sync
 *       GET cannot outlive Cloudflare Free’s non-raisable 100 s origin timeout (524) while holding
 *       the busy gate.
 * </ul>
 *
 * <p><b>Effective duration:</b> {@code days × 24 h + 1 h} — calendar days with DST fall-back
 * tolerance (inclusive calendar {@code from}/{@code to} with exclusive-end conversion). Startup
 * requires {@code max-window-days >= max-sync-window-days}.
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity")
@Validated
public class IntegrityWindowCapProperties {

  /** Default async maximum window length in calendar days (DST-tolerant Instant span). */
  public static final int DEFAULT_MAX_WINDOW_DAYS = 92;

  /**
   * Default sync report-GET maximum window length in calendar days (preserves #743 8×24+1 = 193 h).
   */
  public static final int DEFAULT_MAX_SYNC_WINDOW_DAYS = 8;

  /** Upper bind limit: one leap year of calendar days (no multi-year operator window). */
  public static final int MAX_MAX_WINDOW_DAYS = 366;

  /**
   * Maximum async {@code [from, to)} length in calendar days. Effective Instant cap is {@link
   * #maxWindow()}. Primitive {@code int} so an empty env value cannot remove the bound.
   */
  @Min(1)
  @Max(MAX_MAX_WINDOW_DAYS)
  private int maxWindowDays = DEFAULT_MAX_WINDOW_DAYS;

  /**
   * Maximum synchronous report-GET {@code [from, to)} length in calendar days. Effective Instant
   * cap is {@link #maxSyncWindow()}. Primitive {@code int} so an empty env value cannot remove the
   * bound.
   */
  @Min(1)
  @Max(MAX_MAX_WINDOW_DAYS)
  private int maxSyncWindowDays = DEFAULT_MAX_SYNC_WINDOW_DAYS;

  /**
   * Returns the configured async maximum window length in calendar days.
   *
   * @return max async window days
   */
  public int getMaxWindowDays() {
    return maxWindowDays;
  }

  /**
   * Sets the async maximum window length in calendar days.
   *
   * @param maxWindowDays max days ({@code >= 1})
   */
  public void setMaxWindowDays(int maxWindowDays) {
    this.maxWindowDays = maxWindowDays;
  }

  /**
   * Returns the configured sync report-GET maximum window length in calendar days.
   *
   * @return max sync window days
   */
  public int getMaxSyncWindowDays() {
    return maxSyncWindowDays;
  }

  /**
   * Sets the sync report-GET maximum window length in calendar days.
   *
   * @param maxSyncWindowDays max days ({@code >= 1})
   */
  public void setMaxSyncWindowDays(int maxSyncWindowDays) {
    this.maxSyncWindowDays = maxSyncWindowDays;
  }

  /**
   * Fail-fast when the async cap is below the sync cap (longer windows must use the async job).
   *
   * @return {@code true} when {@code max-window-days >= max-sync-window-days}
   */
  @AssertTrue(
      message =
          "ezkey.audit.integrity.max-window-days must be >= max-sync-window-days (async cap covers"
              + " the sync report-GET cap)")
  public boolean isAsyncCapAtLeastSyncCap() {
    return maxWindowDays >= maxSyncWindowDays;
  }

  /**
   * Effective async Instant-length cap: {@code days × 24 h + 1 h} so the Admin UI lookback Instant
   * span (inclusive calendar {@code from}/{@code to} with exclusive-end conversion) never exceeds
   * the configured days bound across a fall-back DST transition (#743).
   *
   * @return maximum allowed async {@code Duration.between(from, to)}
   */
  public Duration maxWindow() {
    return toInstantCap(maxWindowDays);
  }

  /**
   * Effective sync report-GET Instant-length cap: {@code days × 24 h + 1 h}.
   *
   * @return maximum allowed sync {@code Duration.between(from, to)}
   */
  public Duration maxSyncWindow() {
    return toInstantCap(maxSyncWindowDays);
  }

  /**
   * Validates bounds for async Integrity paths (VERIFY / RUN_VALIDATION / operator retroactive).
   *
   * @param from inclusive window start
   * @param to exclusive window end
   * @throws IllegalArgumentException when bounds are missing or inverted
   * @throws IntegrityWindowOverCapException when the window exceeds {@link #maxWindow()}
   */
  public void validateWindow(OffsetDateTime from, OffsetDateTime to) {
    validateAgainstCap(from, to, maxWindow(), maxWindowDays, false);
  }

  /**
   * Validates bounds for synchronous report GETs ({@code integrity-check}, {@code
   * chain-integrity}).
   *
   * @param from inclusive window start
   * @param to exclusive window end
   * @throws IllegalArgumentException when bounds are missing or inverted
   * @throws IntegrityWindowOverCapException when the window exceeds {@link #maxSyncWindow()}
   *     (includes fixed {@code asyncAlternative} for the Admin API ProblemDetail)
   */
  public void validateSyncWindow(OffsetDateTime from, OffsetDateTime to) {
    validateAgainstCap(from, to, maxSyncWindow(), maxSyncWindowDays, true);
  }

  private static Duration toInstantCap(int days) {
    return Duration.ofHours((long) days * 24L + 1L);
  }

  private static void validateAgainstCap(
      OffsetDateTime from, OffsetDateTime to, Duration max, int maxDays, boolean syncCap) {
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
    if (duration.compareTo(max) > 0) {
      throw syncCap
          ? IntegrityWindowOverCapException.forSyncCap(maxDays)
          : new IntegrityWindowOverCapException(maxDays);
    }
  }
}
