/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: SecurityBeansConfig
 * Description: Provides security-related bean definitions for the core module.
 */

package org.ezkey.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Configuration class for security-related beans.
 *
 * <p>This configuration provides shared security beans used across the Ezkey application, including
 * password encoders for admin authentication and API key secret hashing.
 *
 * <p><b>Provided Beans:</b>
 *
 * <ul>
 *   <li>{@link BCryptPasswordEncoder} - Used for password and API key secret hashing with BCrypt
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see BCryptPasswordEncoder
 */
@Configuration
public class SecurityBeansConfig {

  /**
   * Provides a BCryptPasswordEncoder bean for password and secret hashing.
   *
   * <p>This encoder is used for:
   *
   * <ul>
   *   <li>Admin password hashing (if password authentication is ever needed)
   *   <li>API key secret hashing for secure storage
   * </ul>
   *
   * <p><b>Security Properties:</b>
   *
   * <ul>
   *   <li>Uses BCrypt algorithm with default strength (10 rounds)
   *   <li>Each hash includes a unique salt
   *   <li>Intentionally slow (~100ms) to prevent brute force attacks
   *   <li>One-way hashing (cannot be reversed)
   * </ul>
   *
   * @return a configured BCryptPasswordEncoder instance
   */
  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
