/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: RestAssuredConfig
 * Description: Configuration utility for RestAssured HTTP client
 */

package org.ezkey.tests.util;

import io.restassured.RestAssured;
import org.ezkey.tests.config.DockerStackConfig;

/**
 * Utility class for configuring RestAssured HTTP client settings.
 *
 * <p>Provides methods to configure RestAssured with appropriate timeouts, content types, and base
 * URLs for testing against the Docker stack.
 *
 * @since 2025
 */
public class RestAssuredTestConfig {

  /**
   * Configures RestAssured with default settings for API testing.
   *
   * <p>Sets appropriate logging for REST API calls. RestAssured 6.0.0+ automatically detects and
   * uses Jackson 3.x (tools.jackson) when available.
   */
  public static void configureDefaults() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    // RestAssured 6.0.0+ automatically detects Jackson 3.x (tools.jackson)
    // No explicit configuration needed
  }

  /**
   * Configures RestAssured with Admin API base URL.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public static void configureForAdminApi(DockerStackConfig dockerStackConfig) {
    configureDefaults();
    RestAssured.baseURI = dockerStackConfig.getAdminApiUrl();
    RestAssured.basePath = "/api/v1";
  }

  /**
   * Configures RestAssured with Auth API base URL.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public static void configureForAuthApi(DockerStackConfig dockerStackConfig) {
    configureDefaults();
    RestAssured.baseURI = dockerStackConfig.getAuthApiUrl();
    RestAssured.basePath = "/api/v1";
  }

  /**
   * Configures RestAssured with Crypto API base URL.
   *
   * <p>Used by {@link CryptoApiClient}. After any Crypto API call, tests that then hit Auth API or
   * Admin API must call {@link #configureForAuthApi} or {@link #configureForAdminApi} before the
   * next request.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public static void configureForCryptoApi(DockerStackConfig dockerStackConfig) {
    configureDefaults();
    RestAssured.baseURI = dockerStackConfig.getCryptoApiUrl();
    RestAssured.basePath = "/api/v1/crypto";
  }

  /**
   * Resets RestAssured configuration to defaults.
   *
   * <p>Clears base URI and base path settings.
   */
  public static void reset() {
    RestAssured.baseURI = null;
    RestAssured.basePath = null;
    configureDefaults();
  }
}
