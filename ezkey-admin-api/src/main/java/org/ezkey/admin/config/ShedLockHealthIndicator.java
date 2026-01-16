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
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for ShedLock distributed locks visibility.
 *
 * <p>Provides operational visibility into active distributed locks by querying the ezkey_shedlock table.
 * This helps with monitoring and debugging in HA deployments.
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
public class ShedLockHealthIndicator implements HealthIndicator {

  private final JdbcTemplate jdbcTemplate;

  public ShedLockHealthIndicator(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  /**
   * Returns health status with current lock information.
   *
   * <p>Queries the ezkey_shedlock table to show active locks (where lock_until > NOW()). This provides
   * visibility into which jobs are currently running and which instance is executing them.
   *
   * @return health status with lock details
   */
  @Override
  public Health health() {
    try {
      List<Map<String, Object>> locks =
          jdbcTemplate.queryForList(
              "SELECT name, locked_by, locked_at, lock_until, "
                  + "CASE WHEN lock_until > NOW() THEN 'ACTIVE' ELSE 'EXPIRED' END as status "
                  + "FROM ezkey_shedlock "
                  + "ORDER BY locked_at DESC");

      Health.Builder healthBuilder = Health.up().withDetail("locks", locks);

      // Count active locks
      long activeLocks = locks.stream().filter(lock -> "ACTIVE".equals(lock.get("status"))).count();

      healthBuilder.withDetail("activeLockCount", activeLocks);
      healthBuilder.withDetail("totalLockCount", locks.size());

      return healthBuilder.build();
    } catch (Exception e) {
      return Health.down().withException(e).build();
    }
  }
}
