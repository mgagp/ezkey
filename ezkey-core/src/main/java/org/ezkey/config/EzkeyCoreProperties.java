/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyCoreProperties
 * Description: Configuration properties for ezkey-core module.
 */

package org.ezkey.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the ezkey-core module.
 *
 * <p>This class centralizes all configuration parameters used by the ezkey-core library, providing
 * type-safe configuration with validation and sensible defaults.
 *
 * <p><b>Usage:</b> This configuration class should be used by applications that depend on
 * ezkey-core to configure cryptographic and authentication parameters.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.core")
@Validated
public class EzkeyCoreProperties {

  /** Cryptographic configuration properties. */
  @Valid private final Crypto crypto = new Crypto();

  /** Authentication attempt configuration properties. */
  @Valid private final AuthAttempt authAttempt = new AuthAttempt();

  /** Cryptographic configuration properties. */
  public static class Crypto {

    /**
     * Ed25519 algorithm name constant. Ed25519 keys are always 32 bytes (256 bits) for both private
     * and public keys.
     */
    public static final String ED25519_ALGORITHM = "Ed25519";

    /**
     * Ed25519 key size in bytes (constant). Ed25519 uses fixed-size keys: 32 bytes for both private
     * and public keys.
     */
    public static final int ED25519_KEY_SIZE_BYTES = 32;

    /** Ed25519 signature size in bytes (constant). Ed25519 signatures are always 64 bytes. */
    public static final int ED25519_SIGNATURE_SIZE_BYTES = 64;
  }

  /** Authentication attempt configuration properties. */
  public static class AuthAttempt {

    /** Number of digits for challenge codes. Range: 1-6 digits Default: 2 digits */
    @Min(value = 1, message = "Challenge digits must be at least 1")
    @Max(value = 6, message = "Challenge digits cannot exceed 6")
    private int challengeDigits = 2;

    /** Time-to-live for authentication attempts in seconds. Default: 120 seconds (2 minutes) */
    @Min(value = 30, message = "TTL must be at least 30 seconds")
    @Max(value = 600, message = "TTL cannot exceed 600 seconds (10 minutes)")
    private int ttlSeconds = 120;

    // Getters and setters
    public int getChallengeDigits() {
      return challengeDigits;
    }

    public void setChallengeDigits(int challengeDigits) {
      this.challengeDigits = challengeDigits;
    }

    public int getTtlSeconds() {
      return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
      this.ttlSeconds = ttlSeconds;
    }
  }

  // Getters and setters
  public Crypto getCrypto() {
    return crypto;
  }

  public AuthAttempt getAuthAttempt() {
    return authAttempt;
  }
}
