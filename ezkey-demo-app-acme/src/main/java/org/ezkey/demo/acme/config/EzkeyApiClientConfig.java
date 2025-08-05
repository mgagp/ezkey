/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyApiClientConfig
 * Description: Configuration for Ezkey Admin API client integration.
 */

package org.ezkey.demo.acme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for Ezkey Admin API client integration.
 * <p>
 * This configuration sets up the WebClient for communicating with the Ezkey Admin API
 * (ezkey-admin-api) running on port 9080. It provides the foundation for all
 * integration management operations in the ACME demo application.
 * </p>
 *
 * <p>
 * <b>API Integration:</b> Connects to ezkey-admin-api to manage:
 * <ul>
 * <li>Integration creation and management</li>
 * <li>Enrollment administration</li>
 * <li>Authentication attempt monitoring</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Configuration Properties:</b>
 * <ul>
 * <li><code>ezkey.admin.api.url</code> - Base URL for Ezkey Admin API</li>
 * <li><code>ezkey.simulation.enabled</code> - Enable simulation mode for testing</li>
 * </ul>
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
public class EzkeyApiClientConfig {

    @Value("${ezkey.admin.api.url:http://localhost:9080}")
    private String ezkeyAdminApiUrl;

    @Value("${ezkey.simulation.enabled:false}")
    private boolean simulationEnabled;

    /**
     * Creates a configured WebClient for Ezkey Admin API communication.
     * <p>
     * The WebClient is pre-configured with the base URL and default headers
     * for optimal integration with the Ezkey Admin API. It handles all
     * HTTP communication including JSON serialization and error handling.
     * </p>
     *
     * @return configured WebClient instance for Ezkey API calls
     */
    @Bean("ezkeyAdminApiClient")
    public WebClient ezkeyAdminApiClient() {
        return WebClient.builder()
                .baseUrl(ezkeyAdminApiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "ACME-Demo-App/1.0")
                .build();
    }

    /**
     * Returns the configured Ezkey Admin API base URL.
     *
     * @return the base URL for Ezkey Admin API
     */
    public String getEzkeyAdminApiUrl() {
        return ezkeyAdminApiUrl;
    }

    /**
     * Returns whether simulation mode is enabled.
     * <p>
     * Simulation mode allows testing of the application flow without
     * requiring actual mobile device enrollment. This is useful for
     * development and demonstration purposes.
     * </p>
     *
     * @return true if simulation mode is enabled, false otherwise
     */
    public boolean isSimulationEnabled() {
        return simulationEnabled;
    }
}