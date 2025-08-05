/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: IntegrationService
 * Description: Business service for managing Ezkey integrations via Admin API.
 */

package org.ezkey.demo.acme.service;

import org.ezkey.demo.acme.dto.IntegrationCreateRequestDto;
import org.ezkey.demo.acme.dto.IntegrationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Business service for managing Ezkey integrations via Admin API.
 * <p>
 * This service provides high-level operations for integration management
 * in the ACME demo application. It communicates with the Ezkey Admin API
 * to perform CRUD operations on integrations and handles error scenarios
 * gracefully for demonstration purposes.
 * </p>
 *
 * <p>
 * <b>Integration Operations:</b>
 * <ul>
 * <li>Retrieve all integrations</li>
 * <li>Get specific integration details</li>
 * <li>Create new integrations</li>
 * <li>Delete existing integrations</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Error Handling:</b> Implements robust error handling for network
 * failures and API errors, providing user-friendly error messages for
 * the demo interface.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class IntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationService.class);

    private final WebClient ezkeyAdminApiClient;

    /**
     * Constructor with dependency injection of configured WebClient.
     *
     * @param ezkeyAdminApiClient the configured WebClient for Ezkey Admin API
     */
    public IntegrationService(@Qualifier("ezkeyAdminApiClient") WebClient ezkeyAdminApiClient) {
        this.ezkeyAdminApiClient = ezkeyAdminApiClient;
    }

    /**
     * Retrieves all integrations from Ezkey Admin API.
     * <p>
     * Fetches the complete list of integrations configured in the Ezkey system.
     * This is used to populate the integrations list page in the demo application.
     * </p>
     *
     * @return Flux of IntegrationDto objects representing all integrations
     */
    public Flux<IntegrationDto> getAllIntegrations() {
        logger.debug("Fetching all integrations from Ezkey Admin API");
        
        return ezkeyAdminApiClient
                .get()
                .uri("/api/v1/integrations")
                .retrieve()
                .bodyToFlux(IntegrationDto.class)
                .timeout(Duration.ofSeconds(10))
                .doOnNext(integration -> logger.debug("Received integration: {}", integration.getIntegrationName()))
                .doOnError(error -> logger.error("Error fetching integrations", error))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.warn("API error fetching integrations: {} - {}", ex.getStatusCode(), ex.getMessage());
                    return Flux.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Unexpected error fetching integrations", ex);
                    return Flux.empty();
                });
    }

    /**
     * Retrieves a specific integration by ID.
     * <p>
     * Fetches detailed information about a specific integration including
     * its configuration, keys, and related data. Used for the integration
     * details page in the demo application.
     * </p>
     *
     * @param integrationId the unique identifier of the integration
     * @return Mono of IntegrationDto or empty if not found
     */
    public Mono<IntegrationDto> getIntegrationById(Integer integrationId) {
        logger.debug("Fetching integration with ID: {}", integrationId);
        
        return ezkeyAdminApiClient
                .get()
                .uri("/api/v1/integrations/{id}", integrationId)
                .retrieve()
                .bodyToMono(IntegrationDto.class)
                .timeout(Duration.ofSeconds(10))
                .doOnNext(integration -> logger.debug("Received integration details: {}", integration.getIntegrationName()))
                .doOnError(error -> logger.error("Error fetching integration {}", integrationId, error))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().value() == 404) {
                        logger.warn("Integration {} not found", integrationId);
                        return Mono.empty();
                    }
                    logger.warn("API error fetching integration {}: {} - {}", integrationId, ex.getStatusCode(), ex.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Unexpected error fetching integration {}", integrationId, ex);
                    return Mono.empty();
                });
    }

    /**
     * Creates a new integration in Ezkey Admin API.
     * <p>
     * Submits a new integration creation request to the Ezkey Admin API.
     * The API will generate cryptographic keys and return the complete
     * integration details including the generated integration ID.
     * </p>
     *
     * @param createRequest the integration creation request
     * @return Mono of created IntegrationDto or error
     */
    public Mono<IntegrationDto> createIntegration(IntegrationCreateRequestDto createRequest) {
        logger.debug("Creating new integration: {}", createRequest.getIntegrationName());
        
        return ezkeyAdminApiClient
                .post()
                .uri("/api/v1/integrations")
                .bodyValue(createRequest)
                .retrieve()
                .bodyToMono(IntegrationDto.class)
                .timeout(Duration.ofSeconds(15))
                .doOnNext(integration -> logger.info("Successfully created integration: {} with ID: {}", 
                        integration.getIntegrationName(), integration.getIntegrationId()))
                .doOnError(error -> logger.error("Error creating integration: {}", createRequest.getIntegrationName(), error));
    }

    /**
     * Deletes an integration from Ezkey Admin API.
     * <p>
     * Removes an integration from the Ezkey system. This operation will also
     * remove all associated enrollments and authentication attempts.
     * Used in the demo to clean up test data.
     * </p>
     *
     * @param integrationId the unique identifier of the integration to delete
     * @return Mono<Void> indicating completion or error
     */
    public Mono<Void> deleteIntegration(Integer integrationId) {
        logger.debug("Deleting integration with ID: {}", integrationId);
        
        return ezkeyAdminApiClient
                .delete()
                .uri("/api/v1/integrations/{id}", integrationId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(v -> logger.info("Successfully deleted integration: {}", integrationId))
                .doOnError(error -> logger.error("Error deleting integration {}", integrationId, error));
    }

    /**
     * Convenience method to get all integrations as a List for synchronous operations.
     * <p>
     * This method blocks and converts the reactive Flux to a List for use
     * in traditional Spring MVC controllers that expect synchronous results.
     * </p>
     *
     * @return List of IntegrationDto objects
     */
    public List<IntegrationDto> getAllIntegrationsSync() {
        return getAllIntegrations()
                .collectList()
                .block(Duration.ofSeconds(30));
    }

    /**
     * Convenience method to get an integration by ID synchronously.
     * <p>
     * This method blocks and returns the integration or null if not found.
     * Used in traditional Spring MVC controllers.
     * </p>
     *
     * @param integrationId the unique identifier of the integration
     * @return IntegrationDto or null if not found
     */
    public IntegrationDto getIntegrationByIdSync(Integer integrationId) {
        return getIntegrationById(integrationId)
                .block(Duration.ofSeconds(30));
    }

    /**
     * Convenience method to create an integration synchronously.
     * <p>
     * This method blocks and returns the created integration.
     * Used in traditional Spring MVC controllers.
     * </p>
     *
     * @param createRequest the integration creation request
     * @return created IntegrationDto
     */
    public IntegrationDto createIntegrationSync(IntegrationCreateRequestDto createRequest) {
        return createIntegration(createRequest)
                .block(Duration.ofSeconds(30));
    }
}