/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: CryptoApplication
 * Description: Spring Boot application for Ezkey Crypto API.
 */

package org.ezkey.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for Ezkey Crypto API.
 *
 * <p>This application provides cryptographic endpoints for testing and integration.
 *
 * @since 2025
 */
@SpringBootApplication(
    scanBasePackages = { //
      "org.ezkey.crypto", //
      "org.ezkey.exception", //
      "org.ezkey.signature", //
      "org.ezkey.config", //
    })
public class CryptoApplication {

  /**
   * Main method to start the Ezkey Crypto API application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(CryptoApplication.class, args);
  }
}
