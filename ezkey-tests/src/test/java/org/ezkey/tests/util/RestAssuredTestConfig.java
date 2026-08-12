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
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.path.json.JsonPath;
import org.ezkey.tests.config.DockerStackConfig;

/**
 * Utility class for configuring RestAssured HTTP client settings.
 *
 * <p>Provides methods to configure RestAssured with appropriate timeouts, content types, and base
 * URLs for testing against the Docker stack.
 *
 * <p>Jackson 3 is configured explicitly for REST Assured request/response mapping. JsonPath {@code
 * getObject(..., Class)} still expects Jackson 2 in Rest Assured 6.0.0; use {@link
 * #readNumber(JsonPath, String...)} or {@code jsonPath.get(path)} instead.
 *
 * @since 2025
 */
public class RestAssuredTestConfig {

  /**
   * Configures RestAssured with default settings for API testing.
   *
   * <p>Sets appropriate logging for REST API calls and pins Jackson 3 as the object mapper for Rest
   * Assured serialization.
   */
  public static void configureDefaults() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.config =
        RestAssuredConfig.config()
            .objectMapperConfig(
                ObjectMapperConfig.objectMapperConfig()
                    .defaultObjectMapperType(ObjectMapperType.JACKSON_3));
  }

  /**
   * Reads the first non-null numeric value from JsonPath without {@code getObject}
   * (Jackson-2-only).
   *
   * @param jsonPath response JsonPath
   * @param paths candidate paths in priority order
   * @return first numeric value found, or null
   */
  public static Number readNumber(JsonPath jsonPath, String... paths) {
    for (String path : paths) {
      Object value = jsonPath.get(path);
      if (value instanceof Number number) {
        return number;
      }
    }
    return null;
  }

  /**
   * Reads the first non-null integer value from JsonPath without {@code getObject}.
   *
   * @param jsonPath response JsonPath
   * @param paths candidate paths in priority order
   * @return first integer value found, or null
   */
  public static Integer readInteger(JsonPath jsonPath, String... paths) {
    Number number = readNumber(jsonPath, paths);
    return number == null ? null : number.intValue();
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
   * Configures RestAssured with Integration API base URL (API-key M2M auth attempts).
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public static void configureForIntegrationApi(DockerStackConfig dockerStackConfig) {
    configureDefaults();
    RestAssured.baseURI = dockerStackConfig.getIntegrationApiUrl();
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
