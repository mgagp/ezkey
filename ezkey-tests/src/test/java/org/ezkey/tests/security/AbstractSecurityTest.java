/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Base: AbstractSecurityTest
 * Description: Base class for security tests with common setup
 */

package org.ezkey.tests.security;

import org.ezkey.tests.config.DockerStackConfig;
import org.ezkey.tests.util.AuthTokenManager;
import org.ezkey.tests.util.BootstrapCredentialsExtractor;
import org.ezkey.tests.util.CryptoApiClient;
import org.ezkey.tests.util.RestAssuredTestConfig;
import org.ezkey.tests.util.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for security tests.
 *
 * <p>Provides common setup and teardown for all security tests, including:
 *
 * <ul>
 *   <li>Docker stack configuration and health checks
 *   <li>Authentication token management
 *   <li>Crypto API client initialization
 *   <li>Test data factory initialization
 *   <li>RestAssured configuration reset
 * </ul>
 *
 * <p>All security tests should extend this class to ensure consistent test environment setup.
 *
 * @since 2025
 */
public abstract class AbstractSecurityTest {

  protected static final Logger log = LoggerFactory.getLogger(AbstractSecurityTest.class);

  protected DockerStackConfig dockerStackConfig;
  protected AuthTokenManager authTokenManager;
  protected CryptoApiClient cryptoApiClient;
  protected TestDataFactory testDataFactory;
  protected BootstrapCredentialsExtractor bootstrapCredentialsExtractor;

  /**
   * Sets up test environment before each test.
   *
   * <p>Initializes Docker stack configuration, verifies services are healthy, and sets up utility
   * classes for test execution.
   */
  @BeforeEach
  public void setUp() {
    log.info("Setting up test environment...");

    // Initialize Docker stack configuration
    dockerStackConfig = new DockerStackConfig();

    // Verify services are healthy
    dockerStackConfig.verifyServicesHealthy();

    // Initialize utility classes
    authTokenManager = new AuthTokenManager(dockerStackConfig);
    cryptoApiClient = new CryptoApiClient(dockerStackConfig);
    testDataFactory = new TestDataFactory(dockerStackConfig, authTokenManager);
    bootstrapCredentialsExtractor = new BootstrapCredentialsExtractor();

    // Configure bootstrap dependencies for automatic token acquisition
    authTokenManager.setBootstrapDependencies(bootstrapCredentialsExtractor, cryptoApiClient);

    log.info("Test environment setup complete");
  }

  /**
   * Cleans up after each test.
   *
   * <p>Resets RestAssured configuration to ensure no state leaks between tests.
   */
  @AfterEach
  public void tearDown() {
    log.debug("Cleaning up test environment...");
    RestAssuredTestConfig.reset();
  }
}
