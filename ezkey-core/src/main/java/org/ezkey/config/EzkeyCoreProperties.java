/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyCoreProperties
 * Description: Configuration properties for ezkey-core module.
 */

package org.ezkey.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
  private final Crypto crypto = new Crypto();

  /** Authentication attempt configuration properties. */
  private final AuthAttempt authAttempt = new AuthAttempt();

  /** Cryptographic configuration properties. */
  public static class Crypto {

    /**
     * RSA key size in bits for key pair generation. Minimum: 2048 bits (security requirement)
     * Default: 2048 bits
     */
    @Min(value = 2048, message = "RSA key size must be at least 2048 bits for security")
    private int rsaKeySize = 2048;

    /** RSA algorithm name for key generation and operations. Default: "RSA" */
    @NotBlank(message = "RSA algorithm name cannot be blank")
    private String rsaAlgorithm = "RSA";

    /** Digital signature algorithm for signing and verification. Default: "SHA256withRSA" */
    @NotBlank(message = "Signature algorithm cannot be blank")
    private String signatureAlgorithm = "SHA256withRSA";

    /**
     * Minimum RSA key size for validation (security enforcement). Keys smaller than this will be
     * rejected. Default: 2048 bits
     */
    @Min(value = 2048, message = "Minimum key size must be at least 2048 bits")
    private int minimumKeySize = 2048;

    // Getters and setters
    public int getRsaKeySize() {
      return rsaKeySize;
    }

    public void setRsaKeySize(int rsaKeySize) {
      this.rsaKeySize = rsaKeySize;
    }

    public String getRsaAlgorithm() {
      return rsaAlgorithm;
    }

    public void setRsaAlgorithm(String rsaAlgorithm) {
      this.rsaAlgorithm = rsaAlgorithm;
    }

    public String getSignatureAlgorithm() {
      return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
      this.signatureAlgorithm = signatureAlgorithm;
    }

    public int getMinimumKeySize() {
      return minimumKeySize;
    }

    public void setMinimumKeySize(int minimumKeySize) {
      this.minimumKeySize = minimumKeySize;
    }
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
