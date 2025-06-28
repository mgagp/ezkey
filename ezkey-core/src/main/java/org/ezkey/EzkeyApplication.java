/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: EzkeyApplication
 * Description: Main Spring Boot application class for Ezkey MFA/Passkey system.
 */

package org.ezkey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for Ezkey MFA/Passkey system.
 * <p>
 * This is the entry point for the Ezkey application, a modern MFA/Passkey alternative
 * that provides secure authentication for applications and systems. The application
 * uses Spring Boot's auto-configuration to set up the web server, database connections,
 * and all necessary components for the MFA system.
 * </p>
 *
 * <p>
 * <b>Key Features:</b>
 * <ul>
 *   <li><b>MFA Authentication:</b> Multi-factor authentication with device enrollment</li>
 *   <li><b>Passkey Support:</b> Modern passkey-based authentication</li>
 *   <li><b>Integration Management:</b> Support for multiple application integrations</li>
 *   <li><b>Cryptographic Security:</b> RSA-based digital signatures and encryption</li>
 *   <li><b>RESTful API:</b> Clean REST API for all operations</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Technology Stack:</b>
 * <ul>
 *   <li><b>Framework:</b> Spring Boot 3.x with Spring Web</li>
 *   <li><b>Persistence:</b> Spring Data JPA with Hibernate</li>
 *   <li><b>Database:</b> H2 (development) / PostgreSQL (production)</li>
 *   <li><b>Mapping:</b> MapStruct for object mapping</li>
 *   <li><b>Security:</b> RSA cryptography with SHA-256</li>
 * </ul>
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Main application entry point</p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 */
@SpringBootApplication
public class EzkeyApplication {
    
    /**
     * Main method that starts the Ezkey Spring Boot application.
     * <p>
     * This method initializes the Spring application context, starts the embedded
     * web server, and makes the application ready to handle HTTP requests.
     * </p>
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(EzkeyApplication.class, args);
    }
}
