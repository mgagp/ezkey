/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentService
 * Description: Business service for managing Ezkey enrollments via Admin API.
 */

package org.ezkey.demo.acme.service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.ezkey.demo.acme.generated.dto.EnrollmentCreateRequestDto;
import org.ezkey.demo.acme.generated.dto.EnrollmentCreateResponseDto;
import org.ezkey.demo.acme.generated.dto.EnrollmentResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Business service for managing Ezkey enrollments via Admin API.
 *
 * <p>Provides high-level operations to list, create, retrieve and delete enrollments. This demo
 * service delegates to the Ezkey Admin API using a configured {@link WebClient}.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class EnrollmentService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentService.class);

  private final WebClient ezkeyAdminApiClient;

  /**
   * Creates a new {@link EnrollmentService} with the provided Ezkey Admin API client.
   *
   * @param ezkeyAdminApiClient the configured WebClient bean for Ezkey Admin API
   */
  public EnrollmentService(@Qualifier("ezkeyAdminApiClient") WebClient ezkeyAdminApiClient) {
    this.ezkeyAdminApiClient = ezkeyAdminApiClient;
  }

  /**
   * Retrieves all enrollments from Ezkey Admin API.
   *
   * @return Flux of {@link EnrollmentResponseDto}
   */
  public Flux<EnrollmentResponseDto> getAllEnrollments() {
    logger.debug("Fetching all enrollments from Ezkey Admin API");

    return ezkeyAdminApiClient
        .get()
        .uri("/api/v1/enrollments")
        .retrieve()
        .bodyToFlux(EnrollmentResponseDto.class)
        .timeout(Duration.ofSeconds(10))
        .doOnError(error -> logger.error("Error fetching enrollments", error))
        .onErrorResume(
            WebClientResponseException.class,
            ex -> {
              logger.warn(
                  "API error fetching enrollments: {} - {}", ex.getStatusCode(), ex.getMessage());
              return Flux.empty();
            })
        .onErrorResume(
            Exception.class,
            ex -> {
              logger.error("Unexpected error fetching enrollments", ex);
              return Flux.empty();
            });
  }

  /**
   * Retrieves a single enrollment by its identifier.
   *
   * @param enrollmentId the unique enrollment identifier
   * @return Mono of {@link EnrollmentResponseDto}
   */
  public Mono<EnrollmentResponseDto> getEnrollmentById(Integer enrollmentId) {
    logger.debug("Fetching enrollment with ID: {}", enrollmentId);

    return ezkeyAdminApiClient
        .get()
        .uri("/api/v1/enrollments/{id}", enrollmentId)
        .retrieve()
        .bodyToMono(EnrollmentResponseDto.class)
        .timeout(Duration.ofSeconds(10))
        .doOnError(error -> logger.error("Error fetching enrollment {}", enrollmentId, error))
        .onErrorResume(
            WebClientResponseException.class,
            ex -> {
              if (ex.getStatusCode().value() == 404) {
                logger.warn("Enrollment {} not found", enrollmentId);
                return Mono.empty();
              }
              logger.warn(
                  "API error fetching enrollment {}: {} - {}",
                  enrollmentId,
                  ex.getStatusCode(),
                  ex.getMessage());
              return Mono.empty();
            })
        .onErrorResume(
            Exception.class,
            ex -> {
              logger.error("Unexpected error fetching enrollment {}", enrollmentId, ex);
              return Mono.empty();
            });
  }

  /**
   * Retrieves all enrollments for a given integration by client-side filtering. The Admin API
   * currently exposes only a global list endpoint.
   *
   * @param integrationId the integration identifier to filter on
   * @return Flux of {@link EnrollmentResponseDto} for the specified integration
   */
  public Flux<EnrollmentResponseDto> getEnrollmentsByIntegration(Integer integrationId) {
    Objects.requireNonNull(integrationId, "integrationId must not be null");
    return getAllEnrollments().filter(e -> integrationId.equals(e.getIntegrationId()));
  }

  /**
   * Creates a new enrollment via Ezkey Admin API.
   *
   * @param request the request payload
   * @return Mono of {@link EnrollmentCreateResponseDto}
   */
  public Mono<EnrollmentCreateResponseDto> createEnrollment(EnrollmentCreateRequestDto request) {
    logger.info(
        "Creating new enrollment for integration {} with name {}",
        request.getIntegrationId(),
        request.getName());

    return ezkeyAdminApiClient
        .post()
        .uri("/api/v1/enrollments")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(EnrollmentCreateResponseDto.class)
        .timeout(Duration.ofSeconds(15))
        .doOnError(error -> logger.error("Error creating enrollment: {}", request, error));
  }

  /**
   * Deletes an enrollment by its identifier.
   *
   * @param enrollmentId the enrollment identifier to delete
   * @return Mono<Void> signaling completion
   */
  public Mono<Void> deleteEnrollment(Integer enrollmentId) {
    logger.debug("Deleting enrollment with ID: {}", enrollmentId);

    return ezkeyAdminApiClient
        .delete()
        .uri("/api/v1/enrollments/{id}", enrollmentId)
        .retrieve()
        .bodyToMono(Void.class)
        .timeout(Duration.ofSeconds(10))
        .doOnSuccess(v -> logger.info("Successfully deleted enrollment: {}", enrollmentId))
        .doOnError(error -> logger.error("Error deleting enrollment {}", enrollmentId, error));
  }

  /**
   * Convenience method to block and collect enrollments for an integration.
   *
   * @param integrationId the integration identifier
   * @return list of enrollments for the specified integration
   */
  public List<EnrollmentResponseDto> getEnrollmentsByIntegrationSync(Integer integrationId) {
    return getEnrollmentsByIntegration(integrationId).collectList().block(Duration.ofSeconds(30));
  }
}
