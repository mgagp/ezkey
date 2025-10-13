/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: SimApplication
 * Description: Spring Boot application for Ezkey Sim API.
 */

package org.ezkey.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for Ezkey Sim API.
 *
 * <p>This application provides authentication endpoints for simulations.
 *
 * @since 2025
 */
@SpringBootApplication(
    scanBasePackages = { //
      "org.ezkey.sim", //
      "org.ezkey.exception", //
      "org.ezkey.signature", //
      "org.ezkey.config", //
    })
public class SimApplication {

  /**
   * Main method to start the Ezkey Sim API application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(SimApplication.class, args);
  }
}
