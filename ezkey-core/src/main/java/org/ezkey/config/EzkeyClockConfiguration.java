/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyClockConfiguration
 * Description: UTC clock bean shared by authentication-attempt timing.
 */

package org.ezkey.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the clock used for authentication-attempt creation and cooldown checks.
 *
 * @since 2026
 */
@Configuration
public class EzkeyClockConfiguration {

  /**
   * UTC clock. Tests replace this bean or pass {@link Clock#fixed} to the service.
   *
   * @return the platform clock
   */
  @Bean
  public Clock ezkeyClock() {
    return Clock.systemUTC();
  }
}
