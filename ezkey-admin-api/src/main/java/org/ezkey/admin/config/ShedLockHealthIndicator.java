/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * HealthIndicator: ShedLockHealthIndicator
 * Description: Health indicator for ShedLock distributed locks visibility.
 */

package org.ezkey.admin.config;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for ShedLock distributed locks visibility.
 *
 * <p>Provides operational visibility into active distributed locks by querying the ezkey_shedlock
 * table. This helps with monitoring and debugging in HA deployments.
 *
 * <p><b>Health Endpoint:</b> Accessible via Spring Boot Actuator health endpoint:
 *
 * <pre>
 * GET /actuator/health
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@DependsOnDatabaseInitialization
public class ShedLockHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(ShedLockHealthIndicator.class);

  private static final String LOCKS_QUERY =
      """
      SELECT name,
             locked_by,
             locked_at,
             lock_until,
             CASE WHEN lock_until > CURRENT_TIMESTAMP THEN 'ACTIVE'
                  ELSE 'EXPIRED'
             END AS status
        FROM ezkey_shedlock
       ORDER BY locked_at DESC
      """;

  private final JdbcTemplate jdbcTemplate;

  @Autowired
  public ShedLockHealthIndicator(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  /**
   * Returns health status with current lock information.
   *
   * <p>Queries the ezkey_shedlock table to show active locks (where lock_until > NOW()). This
   * provides visibility into which jobs are currently running and which instance is executing them.
   *
   * @return health status with lock details
   */
  @Override
  public Health health() {
    try {
      List<Map<String, Object>> locks = jdbcTemplate.queryForList(LOCKS_QUERY);
      long activeLocks = locks.stream().filter(lock -> "ACTIVE".equals(lock.get("status"))).count();

      return Health.up()
          .withDetail("locks", locks)
          .withDetail("activeLockCount", activeLocks)
          .withDetail("totalLockCount", locks.size())
          .build();
    } catch (Exception e) {
      LOG.warn("Failed to retrieve ShedLock health details", e);
      return Health.down().withException(e).build();
    }
  }
}
