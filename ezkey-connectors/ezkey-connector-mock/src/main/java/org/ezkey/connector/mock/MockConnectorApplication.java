/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.mock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Test double for the delivery contract v0. It delivers nothing, and it refuses to start outside an
 * explicit test mode, so it cannot pass for real delivery.
 */
@SpringBootApplication
public class MockConnectorApplication {

  /**
   * Starts the mock.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(MockConnectorApplication.class, args);
  }

  /**
   * Refuses to run without the explicit test mode and a token.
   *
   * @param testMode whether test mode was declared
   * @param token the bearer token
   * @return the startup guard
   */
  @Bean
  ApplicationRunner startupGuard(
      @Value("${ezkey.connector.mock.test-mode}") boolean testMode,
      @Value("${ezkey.connector.mock.token}") String token) {
    return _ -> check(testMode, token);
  }

  static void check(boolean testMode, String token) {
    if (!testMode) {
      throw new IllegalStateException(
          "This mock never delivers anything. Set EZKEY_CONNECTOR_MOCK_TEST_MODE=true to run it.");
    }
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("Set EZKEY_CONNECTOR_MOCK_TOKEN to run the mock.");
    }
  }
}
