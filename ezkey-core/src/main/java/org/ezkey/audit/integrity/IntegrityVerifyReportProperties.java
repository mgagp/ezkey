/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: IntegrityVerifyReportProperties
 * Description: Bounds for synchronous Integrity report GETs and VERIFY async starts.
 */

package org.ezkey.audit.integrity;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for Integrity report GET / VERIFY window bounds.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity.verify-report}
 *
 * <p>Default matches {@link IntegrityHeavyCryptoWindowLimits#DEFAULT_MAX_WINDOW_HOURS} (8 calendar
 * days, DST transition included). Provisional hour key — #730 will align with {@code
 * max-window-days}.
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity.verify-report")
@Validated
public class IntegrityVerifyReportProperties {

  /**
   * Maximum {@code [from, to)} length in hours for report GETs and async VERIFY starts. Default
   * {@link IntegrityHeavyCryptoWindowLimits#DEFAULT_MAX_WINDOW_HOURS}.
   */
  @Min(1)
  private int maxWindowHours = IntegrityHeavyCryptoWindowLimits.DEFAULT_MAX_WINDOW_HOURS;

  public int getMaxWindowHours() {
    return maxWindowHours;
  }

  public void setMaxWindowHours(int maxWindowHours) {
    this.maxWindowHours = maxWindowHours;
  }

  /**
   * Validates bounds: required range, ordered ends, and hard window cap.
   *
   * @param from inclusive window start
   * @param to exclusive window end
   * @throws IllegalArgumentException when bounds are missing, inverted, or exceed the cap
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
    if (duration.compareTo(Duration.ofHours(maxWindowHours)) > 0) {
      throw new IllegalArgumentException(
          "Verification window exceeds maximum of "
              + maxWindowHours
              + " hours (8 calendar days, DST transition included). Narrow the range.");
    }
  }
}
