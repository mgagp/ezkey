/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component Type: KarateTestRunner
 * Description: JUnit 5 runner for Karate BDD tests with embedded servers
 */

package org.ezkey.tests;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main test runner for Karate BDD functional tests.
 *
 * <p>This class manages the lifecycle of embedded servers and executes all Karate feature files. It
 * uses JUnit 5 lifecycle annotations to start servers before tests and stop them after.
 *
 * <p>Test execution:
 *
 * <ul>
 *   <li>BeforeAll: Starts Admin API, Auth API, and PostgreSQL
 *   <li>Test: Runs all Karate feature files
 *   <li>AfterAll: Stops all servers and database
 * </ul>
 *
 * @since 2025
 */
public class KarateTestRunner {

  private static final Logger log = LoggerFactory.getLogger(KarateTestRunner.class);

  private static EmbeddedServerManager serverManager;

  /**
   * Sets up embedded servers before running tests.
   *
   * <p>This method starts PostgreSQL, runs migrations, and starts both Admin and Auth APIs.
   *
   * @throws Exception if servers fail to start
   */
  @BeforeAll
  public static void beforeAll() throws Exception {
    log.info("Setting up embedded servers for Karate tests...");

    serverManager = new EmbeddedServerManager();
    serverManager.startAll();

    // Store URLs as system properties for Karate
    System.setProperty("admin.url", serverManager.getAdminUrl());
    System.setProperty("auth.url", serverManager.getAuthUrl());
    System.setProperty("db.url", serverManager.getDatabaseUrl());

    log.info("Embedded servers ready for testing");
  }

  /**
   * Tears down embedded servers after all tests complete.
   *
   * <p>Ensures graceful shutdown of all components.
   */
  @AfterAll
  public static void afterAll() {
    log.info("Tearing down embedded servers...");

    if (serverManager != null) {
      serverManager.stopAll();
    }

    log.info("Cleanup complete");
  }

  /**
   * Runs all Karate feature files.
   *
   * <p>This test method executes all feature files in the karate directory and asserts that no
   * failures occurred.
   */
  @Test
  public void testAll() {
    log.info("Running Karate tests...");

    Results results =
        Runner.path("classpath:karate")
            .tags("~@ignore")
            .parallel(1); // Sequential execution for now

    log.info(
        "Karate tests completed - Features: {}, Scenarios: {}, Passed: {}, Failed: {}",
        results.getFeaturesTotal(),
        results.getScenariosTotal(),
        results.getScenariosPassed(),
        results.getScenariosFailed());

    Assertions.assertEquals(0, results.getFailCount(), results.getErrorMessages());
  }
}
