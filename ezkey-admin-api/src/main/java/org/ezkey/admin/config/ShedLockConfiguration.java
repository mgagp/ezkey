/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: ShedLockConfiguration
 * Description: Configuration for ShedLock distributed job locking.
 */

package org.ezkey.admin.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuration for ShedLock distributed job locking.
 *
 * <p>Enables distributed lock coordination for scheduled jobs across multiple Admin API instances
 * in HA deployments. Uses PostgreSQL as the lock provider.
 *
 * <p><b>Lock Behavior:</b>
 *
 * <ul>
 *   <li>defaultLockAtMostFor: Maximum lock duration (prevents deadlock if instance crashes)
 *   <li>Each job can override with @SchedulerLock(lockAtMostFor, lockAtLeastFor)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfiguration {

  /**
   * Creates the JDBC-based lock provider using the application's DataSource.
   *
   * <p>Uses database time (usingDbTime()) for consistency across instances, ensuring lock expiry
   * timestamps are consistent even if instance clocks drift.
   *
   * @param dataSource the PostgreSQL data source
   * @return configured lock provider
   */
  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .withTableName("ezkey_shedlock") // Use ezkey_ prefix for consistency
            .usingDbTime() // Use database time for consistency across instances
            .build());
  }
}
