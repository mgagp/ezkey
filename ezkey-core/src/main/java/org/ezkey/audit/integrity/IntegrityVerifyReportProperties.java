/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: IntegrityVerifyReportProperties
 * Description: Bounds for synchronous Integrity report GETs (chain-integrity / integrity-check).
 */

package org.ezkey.audit.integrity;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for synchronous Integrity report GETs.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity.verify-report}
 *
 * <p>These GETs recompute chain/entry integrity in the request thread. The default window cap
 * matches the Admin UI default 7-day lookback Instant span (inclusive calendar {@code from}/{@code
 * to} with exclusive-end conversion yields up to 8×24h). Unbounded ranges must use async VERIFY
 * jobs; job-backed report hydration is tracked separately (#730).
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity.verify-report")
public class IntegrityVerifyReportProperties {

  /**
   * Maximum {@code [from, to)} length in hours for {@code GET …/integrity-check} and {@code GET
   * …/chain-integrity}. Default {@code 192} (8 days).
   */
  private int maxWindowHours = 192;

  public int getMaxWindowHours() {
    return maxWindowHours;
  }

  public void setMaxWindowHours(int maxWindowHours) {
    this.maxWindowHours = maxWindowHours;
  }

  /**
   * Validates report GET bounds: required range, ordered ends, and hard window cap.
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
              + " hours (synchronous report GET cap). Narrow the range or use an Integrity async"
              + " VERIFY job.");
    }
  }
}
