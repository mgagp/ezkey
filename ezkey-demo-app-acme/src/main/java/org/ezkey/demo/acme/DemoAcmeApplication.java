/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: AcmeDemoApplication
 * Description: Main Spring Boot application for ACME demo integrating with Ezkey.
 */

package org.ezkey.demo.acme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for ACME demo.
 * <p>
 * This application simulates a protected web application (ACME Inc) that integrates
 * with Ezkey for multi-factor authentication. It demonstrates the complete Ezkey
 * integration flow including creating integrations, enrollments, and authentication
 * attempts through a user-friendly web interface.
 * </p>
 *
 * <p>
 * <b>Demo Purpose:</b> Provides a realistic demonstration of Ezkey MFA integration
 * for potential users and developers. Shows how an external application can leverage
 * Ezkey's API to implement secure authentication flows.
 * </p>
 *
 * <p>
 * <b>Technical Stack:</b>
 * <ul>
 * <li><b>Backend:</b> Spring Boot 3.3.6, Java 21</li>
 * <li><b>Frontend:</b> Thymeleaf, htmx, Alpine.js</li>
 * <li><b>UI Design:</b> Neo Brutalism styling</li>
 * <li><b>Port:</b> 8082</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Integration:</b> Communicates with ezkey-admin-api (port 9080) to demonstrate
 * the complete authentication workflow from integration setup to user authentication.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootApplication
public class DemoAcmeApplication {

    /**
     * Main method to start the ACME demo application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoAcmeApplication.class, args);
    }
}