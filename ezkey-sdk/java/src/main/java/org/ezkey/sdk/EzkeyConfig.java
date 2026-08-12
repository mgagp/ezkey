/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyConfig
 * Description: Immutable configuration record for the Ezkey Java SDK.
 */

package org.ezkey.sdk;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for the Ezkey SDK.
 *
 * <p>Contains all parameters needed to connect to an Ezkey Integration API instance for API-key
 * (M2M) auth-attempt flows. The caller is responsible for providing values from whatever external
 * source they prefer (properties, YAML, environment variables, database, hardcoded, etc.) — the SDK
 * makes no assumption about the storage format.
 *
 * @param baseUrl the Integration API base URL (e.g. {@code http://localhost:7080})
 * @param integrationKey the public integration key (e.g. {@code ezkey_ikey_xxx})
 * @param secretKey the secret key (e.g. {@code ezkey_skey_xxx})
 * @param connectTimeout HTTP connection timeout
 * @param readTimeout HTTP read/response timeout
 * @since 2025
 */
public record EzkeyConfig(
    String baseUrl,
    String integrationKey,
    String secretKey,
    Duration connectTimeout,
    Duration readTimeout) {

  /** Default Integration API base URL (canonical M2M surface). */
  public static final String DEFAULT_BASE_URL = "http://localhost:7080";

  /** Default connection timeout (10 seconds). */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /** Default read timeout (30 seconds). */
  public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Compact constructor with validation.
   *
   * @throws NullPointerException if any required parameter is null
   * @throws IllegalArgumentException if integration key or secret key is blank
   */
  public EzkeyConfig {
    Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    Objects.requireNonNull(integrationKey, "integrationKey must not be null");
    Objects.requireNonNull(secretKey, "secretKey must not be null");
    Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
    Objects.requireNonNull(readTimeout, "readTimeout must not be null");

    if (integrationKey.isBlank()) {
      throw new IllegalArgumentException("integrationKey must not be blank");
    }
    if (secretKey.isBlank()) {
      throw new IllegalArgumentException("secretKey must not be blank");
    }
    if (baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be blank");
    }
  }

  /**
   * Creates a configuration with defaults for base URL and timeouts.
   *
   * @param integrationKey the integration key
   * @param secretKey the secret key
   * @return a new configuration with default URL and timeouts
   */
  public static EzkeyConfig of(String integrationKey, String secretKey) {
    return new EzkeyConfig(
        DEFAULT_BASE_URL, integrationKey, secretKey, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
  }
}
