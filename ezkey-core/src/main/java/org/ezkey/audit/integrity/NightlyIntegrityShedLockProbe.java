/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: NightlyIntegrityShedLockProbe
 * Description: Cross-replica nightly-running detection via ezkey_shedlock.
 */

package org.ezkey.audit.integrity;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads whether ShedLock still holds {@code NIGHTLY_INTEGRITY_VALIDATION} (cross-replica).
 *
 * <p>Uses database time ({@code CURRENT_TIMESTAMP}) so lock expiry matches {@code usingDbTime()} in
 * the ShedLock JDBC provider. After a crash, {@code lock_until} may remain in the future for up to
 * {@code lockAtMostFor=PT2H} — treated as nightly-running (fail-closed).
 *
 * @since 2026
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.audit.integrity.async-job.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class NightlyIntegrityShedLockProbe {

  private static final Logger logger = LoggerFactory.getLogger(NightlyIntegrityShedLockProbe.class);

  /** Matches {@code @SchedulerLock(name = "NIGHTLY_INTEGRITY_VALIDATION", …)}. */
  public static final String NIGHTLY_LOCK_NAME = "NIGHTLY_INTEGRITY_VALIDATION";

  private static final String ACTIVE_QUERY =
      """
      SELECT lock_until > CURRENT_TIMESTAMP
        FROM ezkey_shedlock
       WHERE name = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Constructs the probe.
   *
   * @param dataSource Admin API DataSource (grants already include ezkey_shedlock)
   */
  public NightlyIntegrityShedLockProbe(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  /**
   * Whether the nightly ShedLock row is currently active.
   *
   * @return true when a row exists and {@code lock_until} is still in the future
   */
  public boolean isNightlyLockActive() {
    try {
      Boolean active =
          jdbcTemplate.query(
              ACTIVE_QUERY,
              rs -> {
                if (!rs.next()) {
                  return false;
                }
                return rs.getBoolean(1);
              },
              NIGHTLY_LOCK_NAME);
      return Boolean.TRUE.equals(active);
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn(
          "ShedLock nightly probe failed (fail-open to idle): {}", ex.getClass().getSimpleName());
      return false;
    }
  }
}
